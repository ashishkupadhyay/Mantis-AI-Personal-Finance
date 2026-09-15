package io.github.ashishkupadhyay.mantis.core.domain.security

import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.model.preferences.AppLockPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the device can run the App Lock prompt (BIOMETRIC_STRONG or device credential). Implemented by the app. */
fun interface DeviceLockAvailability {
    fun canAuthenticate(): Boolean
}

/** The lock as the UI and workers see it (doc 02 §9). */
enum class LockState {
    /** App Lock is disabled in preferences; nothing to authenticate. */
    DISABLED,

    /** Content must not compose until [AppLockManager.unlock] succeeds. */
    LOCKED,

    UNLOCKED,
}

/**
 * Process-scoped lock state (FR-ONB-6, NFR-20h). Starts **locked** so a restored process never renders content
 * before authentication; [applyPreferences] disables it when App Lock is off. The app reports foreground
 * transitions; the manager re-locks once the configured timeout has elapsed in the background.
 */
interface AppLockManager {
    val state: StateFlow<LockState>

    /** True only while unlocked and in the foreground — the condition Strict-vault work requires (FR-LLM-2 g). */
    val isUnlockedForeground: Boolean

    fun applyPreferences(preferences: AppLockPreferences)

    /** Called after a successful biometric / device-credential prompt. */
    fun unlock()

    /** Manual lock (privacy menu) or a failed re-authentication. */
    fun lock()

    fun onForeground()

    fun onBackground()
}

@Singleton
class DefaultAppLockManager @Inject constructor(private val clock: Clock) : AppLockManager {

    private val _state = MutableStateFlow(LockState.LOCKED)
    private var preferences = AppLockPreferences()
    private var backgroundedAt: Long? = null
    private var inForeground = false

    override val state: StateFlow<LockState> = _state.asStateFlow()

    override val isUnlockedForeground: Boolean
        get() = inForeground && (_state.value == LockState.UNLOCKED || _state.value == LockState.DISABLED)

    override fun applyPreferences(preferences: AppLockPreferences) {
        val wasEnabled = this.preferences.enabled
        this.preferences = preferences
        when {
            !preferences.enabled -> _state.value = LockState.DISABLED
            // Turning the lock on from Settings happens while the user is authenticated; don't lock them out.
            !wasEnabled && _state.value == LockState.DISABLED -> _state.value = LockState.UNLOCKED
        }
    }

    override fun unlock() {
        _state.update { if (it == LockState.LOCKED) LockState.UNLOCKED else it }
    }

    override fun lock() {
        if (preferences.enabled) _state.value = LockState.LOCKED
    }

    override fun onForeground() {
        inForeground = true
        val since = backgroundedAt
        backgroundedAt = null
        if (preferences.enabled && since != null && clock.epochMillis() - since >= preferences.timeout.seconds * MILLIS_PER_SECOND) {
            _state.value = LockState.LOCKED
        }
    }

    override fun onBackground() {
        inForeground = false
        backgroundedAt = clock.epochMillis()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
