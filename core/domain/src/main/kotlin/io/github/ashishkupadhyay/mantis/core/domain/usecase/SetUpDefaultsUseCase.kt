package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.BudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import javax.inject.Inject

/**
 * Onboarding's "set me up" step (FR-ONB-8/9): seeds the bundled taxonomy, optionally creates one sample budget
 * in the home currency, and records the currency and month-start choices.
 */
class SetUpDefaultsUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val preferences: PreferencesRepository,
    private val ids: UuidV7,
    private val clock: Clock,
) {

    data class Request(
        val currencyCode: String,
        val monthStartDay: Int,
        val createSampleBudget: Boolean,
        val appLockEnabled: Boolean = false,
    )

    suspend operator fun invoke(request: Request) {
        preferences.update {
            it.copy(
                currencyCode = request.currencyCode,
                monthStartDay = request.monthStartDay,
                appLock = it.appLock.copy(enabled = request.appLockEnabled),
            )
        }
        categories.ensureDefaults()
        if (request.createSampleBudget && budgets.budgets().isEmpty()) {
            val currency = Currency.of(request.currencyCode)
            budgets.save(
                Budget(
                    id = BudgetId(ids.nextString()),
                    name = SAMPLE_BUDGET_NAME,
                    amount = Money.ofMajor(SAMPLE_BUDGET_MAJOR, currency),
                    startsOn = clock.today(),
                    meta = SyncMeta.unstamped(),
                ),
            )
        }
        preferences.update { it.copy(onboardingCompleted = true) }
    }

    private companion object {
        const val SAMPLE_BUDGET_NAME = "Monthly spending"
        const val SAMPLE_BUDGET_MAJOR = 30_000L
    }
}
