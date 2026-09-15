package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.time.SystemClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeBudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SetUpDefaultsUseCaseTest {

    private val categories = FakeCategoryRepository()
    private val budgets = FakeBudgetRepository()
    private val preferences = FakePreferencesRepository()
    private val useCase = SetUpDefaultsUseCase(categories, budgets, preferences, UuidV7(), SystemClock())

    @Test
    fun `FR_ONB_8_9 seeds taxonomy, records choices and creates the sample budget once`() = runTest {
        val request = SetUpDefaultsUseCase.Request(
            currencyCode = "INR", monthStartDay = 25, createSampleBudget = true, appLockEnabled = true,
        )

        useCase(request)
        useCase(request)

        categories.categories() shouldHaveSize DefaultTaxonomy.groups.size + DefaultTaxonomy.leaves.size
        budgets.budgets() shouldHaveSize 1
        budgets.budgets().single().amount.minor shouldBe 30_000_00L
        preferences.state.value.monthStartDay shouldBe 25
        preferences.state.value.onboardingCompleted shouldBe true
        preferences.state.value.appLock.enabled shouldBe true
    }

    @Test
    fun `declining the sample budget creates none`() = runTest {
        useCase(SetUpDefaultsUseCase.Request("USD", 1, createSampleBudget = false))

        budgets.budgets() shouldHaveSize 0
        preferences.state.value.currencyCode shouldBe "USD"
    }
}
