package io.github.ashishkupadhyay.mantis.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.money.NumberStyle
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.security.AppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.security.DeviceLockAvailability
import io.github.ashishkupadhyay.mantis.core.model.preferences.ColorSourcePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.LockTimeout
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val appLockAvailable: Boolean = false,
    val isDebugBuild: Boolean = false,
    val seeding: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface SettingsEvent {
    data class ThemeChanged(val theme: ThemePreference) : SettingsEvent
    data class ColorSourceChanged(val source: ColorSourcePreference) : SettingsEvent
    data class SeedChanged(val seedName: String) : SettingsEvent
    data class AmoledChanged(val enabled: Boolean) : SettingsEvent
    data class ReducedMotionChanged(val enabled: Boolean) : SettingsEvent
    data class CurrencyChanged(val code: String) : SettingsEvent
    data class NumberStyleChanged(val style: NumberStyle) : SettingsEvent
    data class MonthStartChanged(val day: Int) : SettingsEvent
    data class FirstDayOfWeekChanged(val isoDay: Int) : SettingsEvent
    data class AppLockChanged(val enabled: Boolean) : SettingsEvent
    data class LockTimeoutChanged(val timeout: LockTimeout) : SettingsEvent
    data class SecureScreensChanged(val enabled: Boolean) : SettingsEvent
    data class HideAmountsChanged(val enabled: Boolean) : SettingsEvent
    data object LockNow : SettingsEvent
    data object SeedSampleData : SettingsEvent
}

sealed interface SettingsEffect {
    data class Message(val text: String) : SettingsEffect
}

/** FR-SET-1/2/5, FR-ONB-6/7, FR-PRV-6: every toggle writes straight to preferences; the shell reacts live. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val development: DevelopmentRepository,
    private val appLock: AppLockManager,
    lockAvailability: DeviceLockAvailability,
) : ViewModel() {

    private val seeding = MutableStateFlow(false)
    private val lockAvailable = lockAvailability.canAuthenticate()

    val state: StateFlow<SettingsUiState> = combine(preferences.preferences, seeding) { prefs, seeding ->
        SettingsUiState(
            preferences = prefs,
            appLockAvailable = lockAvailable,
            isDebugBuild = development.isDebugBuild,
            seeding = seeding,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ThemeChanged -> update { it.copy(theme = event.theme) }
            is SettingsEvent.ColorSourceChanged -> update { it.copy(colorSource = event.source) }
            is SettingsEvent.SeedChanged -> update { it.copy(colorSource = ColorSourcePreference.SEED, seedName = event.seedName) }
            is SettingsEvent.AmoledChanged -> update { it.copy(amoledBlack = event.enabled) }
            is SettingsEvent.ReducedMotionChanged -> update { it.copy(reducedMotion = event.enabled) }
            is SettingsEvent.CurrencyChanged -> update { it.copy(currencyCode = event.code) }
            is SettingsEvent.NumberStyleChanged -> update { it.copy(numberStyle = event.style) }
            is SettingsEvent.MonthStartChanged ->
                update { it.copy(monthStartDay = event.day.coerceIn(1, UserPreferences.MAX_MONTH_START_DAY)) }
            is SettingsEvent.FirstDayOfWeekChanged ->
                update { it.copy(firstDayOfWeek = event.isoDay.coerceIn(1, UserPreferences.DAYS_IN_WEEK)) }
            is SettingsEvent.AppLockChanged -> update { it.copy(appLock = it.appLock.copy(enabled = event.enabled && lockAvailable)) }
            is SettingsEvent.LockTimeoutChanged -> update { it.copy(appLock = it.appLock.copy(timeout = event.timeout)) }
            is SettingsEvent.SecureScreensChanged -> update { it.copy(secureSensitiveScreens = event.enabled) }
            is SettingsEvent.HideAmountsChanged -> update { it.copy(hideAmounts = event.enabled) }
            SettingsEvent.LockNow -> appLock.lock()
            SettingsEvent.SeedSampleData -> seedSampleData()
        }
    }

    private fun update(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }

    private fun seedSampleData() {
        if (seeding.value) return
        seeding.value = true
        viewModelScope.launch {
            val message = when (val result = development.seedSampleData()) {
                is Outcome.Success -> "Added ${result.value} sample transactions"
                is Outcome.Failure -> result.error.message
            }
            seeding.value = false
            _effects.send(SettingsEffect.Message(message))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
