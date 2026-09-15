package io.github.ashishkupadhyay.mantis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessage
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.security.AppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.security.LockState
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the shell needs before it can render anything: preferences (theme, onboarding) and the lock state. */
data class MainUiState(
    val preferences: UserPreferences = UserPreferences(),
    val lockState: LockState = LockState.LOCKED,
    val loaded: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: PreferencesRepository,
    private val appLock: AppLockManager,
    userMessages: UserMessages,
) : ViewModel() {

    /** Snackbars posted by any feature (undo after delete, "saved", errors) — the shell shows them. */
    val messages: Flow<UserMessage> = userMessages.messages

    val state: StateFlow<MainUiState> = combine(preferences.preferences, appLock.state) { prefs, lock ->
        MainUiState(preferences = prefs, lockState = lock, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    fun unlock() = appLock.unlock()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
