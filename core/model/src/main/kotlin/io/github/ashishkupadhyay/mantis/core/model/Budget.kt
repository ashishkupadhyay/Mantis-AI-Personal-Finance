package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.LocalDate

enum class BudgetPeriodType { MONTHLY, WEEKLY, YEARLY, CUSTOM }

/**
 * A spending limit over a recurring period (FR-BUD-1). An empty [categoryIds] means "all spending";
 * an empty [accountIds] means every account. [thresholds] are percentages that trigger alerts (FR-BUD-4).
 * [startsOn] is the first day the budget counts: rollover accumulates from the period containing it, never from
 * months that predate the budget.
 */
data class Budget(
    val id: BudgetId,
    val name: String,
    val amount: Money,
    val period: BudgetPeriodType = BudgetPeriodType.MONTHLY,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val rollover: Boolean = false,
    val thresholds: List<Int> = DEFAULT_THRESHOLDS,
    val alertOnProjectedOverspend: Boolean = true,
    val categoryIds: Set<CategoryId> = emptySet(),
    val accountIds: Set<AccountId> = emptySet(),
    val snoozedUntilPeriodKey: String? = null,
    val startsOn: LocalDate? = null,
    val meta: SyncMeta,
) {
    init {
        require(name.isNotBlank()) { "Budget name must not be blank" }
        require(amount.minor > 0) { "Budget amount must be positive" }
        require(thresholds.all { it in 1..MAX_THRESHOLD } && thresholds == thresholds.sorted()) {
            "thresholds must be ascending percentages 1..$MAX_THRESHOLD"
        }
        if (period == BudgetPeriodType.CUSTOM) {
            requireNotNull(customStart) { "custom period needs a start" }
            requireNotNull(customEnd) { "custom period needs an end" }
            require(!customEnd.isBefore(customStart)) { "custom period end before start" }
        }
    }

    val coversAllSpending: Boolean get() = categoryIds.isEmpty()

    companion object {
        val DEFAULT_THRESHOLDS: List<Int> = listOf(50, 80, 100)
        private const val MAX_THRESHOLD = 200

        /** Pseudo-threshold logged when the "projected to exceed" alert fires, so it too notifies once per period. */
        const val PROJECTED_ALERT: Int = 1_000
    }
}
