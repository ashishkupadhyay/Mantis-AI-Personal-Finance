package io.github.ashishkupadhyay.mantis.feature.onboarding

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.time.SystemClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeBudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.usecase.SetUpDefaultsUseCase
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** FR-ONB-1/2/6/8/9 at the ViewModel level; the screen is covered by previews and screenshots. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository()
    private val budgets = FakeBudgetRepository()
    private val preferences = FakePreferencesRepository()

    private fun viewModel(lockAvailable: Boolean = true) = OnboardingViewModel(
        setUpDefaults = SetUpDefaultsUseCase(categories, budgets, preferences, UuidV7(), SystemClock()),
        lockAvailability = { lockAvailable },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_ONB_1_skipJumpsToSetupAndNextBackMoveWithinBounds() {
        val vm = viewModel()

        vm.onEvent(OnboardingEvent.Back)
        vm.state.value.page shouldBe 0
        vm.onEvent(OnboardingEvent.Next)
        vm.state.value.page shouldBe 1
        vm.onEvent(OnboardingEvent.Skip)
        vm.state.value.isSetup shouldBe true
        vm.onEvent(OnboardingEvent.Next)
        vm.state.value.page shouldBe OnboardingUiState.SETUP_PAGE
    }

    @Test
    fun FR_ONB_6_appLockCannotBeEnabledWithoutADeviceLock() {
        val vm = viewModel(lockAvailable = false)

        vm.onEvent(OnboardingEvent.AppLockChanged(true))

        vm.state.value.appLockEnabled shouldBe false
        vm.state.value.appLockAvailable shouldBe false
    }

    @Test
    fun FR_ONB_2_8_9_continueLocalOnlyWritesChoicesSeedsDefaultsAndFinishes() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.CurrencyChanged("USD"))
        vm.onEvent(OnboardingEvent.MonthStartChanged(31))
        vm.onEvent(OnboardingEvent.AppLockChanged(true))

        vm.effects.test {
            vm.onEvent(OnboardingEvent.ContinueLocalOnly)
            vm.onEvent(OnboardingEvent.ContinueLocalOnly) // double tap is ignored while saving
            awaitItem() shouldBe OnboardingEffect.Finished
            expectNoEvents()
        }

        val prefs = preferences.state.value
        prefs.currencyCode shouldBe "USD"
        prefs.monthStartDay shouldBe 28
        prefs.appLock.enabled shouldBe true
        prefs.onboardingCompleted shouldBe true
        prefs.localOnlyMode shouldBe true
        categories.categories().isNotEmpty() shouldBe true
        budgets.budgets() shouldHaveSize 1
    }
}
