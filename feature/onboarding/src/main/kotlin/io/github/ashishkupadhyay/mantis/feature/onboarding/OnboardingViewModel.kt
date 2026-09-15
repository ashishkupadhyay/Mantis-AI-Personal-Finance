package io.github.ashishkupadhyay.mantis.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.domain.security.DeviceLockAvailability
import io.github.ashishkupadhyay.mantis.core.domain.usecase.SetUpDefaultsUseCase
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    /** 0..2 are the intro pages, [SETUP_PAGE] the setup form. */
    val page: Int = 0,
    val currencyCode: String = "INR",
    val monthStartDay: Int = 1,
    val createDefaults: Boolean = true,
    val appLockEnabled: Boolean = false,
    val appLockAvailable: Boolean = false,
    val saving: Boolean = false,
) {
    val isSetup: Boolean get() = page == SETUP_PAGE

    companion object {
        const val INTRO_PAGES = 3
        const val SETUP_PAGE = INTRO_PAGES
        val CURRENCIES: ImmutableList<String> = persistentListOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD")
    }
}

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object Skip : OnboardingEvent
    data class CurrencyChanged(val code: String) : OnboardingEvent
    data class MonthStartChanged(val day: Int) : OnboardingEvent
    data class CreateDefaultsChanged(val enabled: Boolean) : OnboardingEvent
    data class AppLockChanged(val enabled: Boolean) : OnboardingEvent
    data object ContinueLocalOnly : OnboardingEvent
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}

/** FR-ONB-1/2/6/8/9: three intro pages with Skip, then the setup form; "Continue without account" completes it. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val setUpDefaults: SetUpDefaultsUseCase,
    lockAvailability: DeviceLockAvailability,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(appLockAvailable = lockAvailability.canAuthenticate()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> _state.update { it.copy(page = (it.page + 1).coerceAtMost(OnboardingUiState.SETUP_PAGE)) }
            OnboardingEvent.Back -> _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
            OnboardingEvent.Skip -> _state.update { it.copy(page = OnboardingUiState.SETUP_PAGE) }
            is OnboardingEvent.CurrencyChanged -> _state.update { it.copy(currencyCode = event.code) }
            is OnboardingEvent.MonthStartChanged ->
                _state.update { it.copy(monthStartDay = event.day.coerceIn(1, UserPreferences.MAX_MONTH_START_DAY)) }
            is OnboardingEvent.CreateDefaultsChanged -> _state.update { it.copy(createDefaults = event.enabled) }
            is OnboardingEvent.AppLockChanged -> _state.update { it.copy(appLockEnabled = event.enabled && it.appLockAvailable) }
            OnboardingEvent.ContinueLocalOnly -> finish()
        }
    }

    private fun finish() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val s = _state.value
            setUpDefaults(
                SetUpDefaultsUseCase.Request(
                    currencyCode = s.currencyCode,
                    monthStartDay = s.monthStartDay,
                    createSampleBudget = s.createDefaults,
                    appLockEnabled = s.appLockEnabled,
                ),
            )
            _effects.send(OnboardingEffect.Finished)
        }
    }
}
