package io.github.ashishkupadhyay.mantis.feature.settings

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeDevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.security.DefaultAppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.security.LockState
import io.github.ashishkupadhyay.mantis.core.model.preferences.LockTimeout
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = FakePreferencesRepository()
    private val development = FakeDevelopmentRepository()
    private val appLock = DefaultAppLockManager(FixedClock(Instant.EPOCH))

    private fun viewModel(lockAvailable: Boolean = true) = SettingsViewModel(preferences, development, appLock, { lockAvailable })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_SET_1_2_eventsWriteThroughToPreferences() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(SettingsEvent.ThemeChanged(ThemePreference.DARK))
        vm.onEvent(SettingsEvent.SeedChanged("ROSE"))
        vm.onEvent(SettingsEvent.MonthStartChanged(40))
        vm.onEvent(SettingsEvent.LockTimeoutChanged(LockTimeout.FIVE_MINUTES))
        advanceUntilIdle()

        val prefs: UserPreferences = preferences.preferences.first()
        prefs.theme shouldBe ThemePreference.DARK
        prefs.seedName shouldBe "ROSE"
        prefs.monthStartDay shouldBe UserPreferences.MAX_MONTH_START_DAY
        prefs.appLock.timeout shouldBe LockTimeout.FIVE_MINUTES
    }

    @Test
    fun FR_ONB_6_appLockStaysOffWithoutADeviceLockAndLockNowLocks() = runTest(dispatcher) {
        val vm = viewModel(lockAvailable = false)
        vm.onEvent(SettingsEvent.AppLockChanged(true))
        advanceUntilIdle()
        preferences.preferences.first().appLock.enabled shouldBe false

        val enabledVm = viewModel(lockAvailable = true)
        enabledVm.onEvent(SettingsEvent.AppLockChanged(true))
        advanceUntilIdle()
        preferences.preferences.first().appLock.enabled shouldBe true
        appLock.applyPreferences(preferences.preferences.first().appLock)
        appLock.unlock()
        enabledVm.onEvent(SettingsEvent.LockNow)
        appLock.state.value shouldBe LockState.LOCKED
    }

    @Test
    fun seedingReportsTheResultOnceAndIgnoresDoubleTaps() = runTest(dispatcher) {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(SettingsEvent.SeedSampleData)
            vm.onEvent(SettingsEvent.SeedSampleData)
            awaitItem() shouldBe SettingsEffect.Message("Added 1800 sample transactions")
            expectNoEvents()
        }
        development.seeded shouldBe 1
    }
}
