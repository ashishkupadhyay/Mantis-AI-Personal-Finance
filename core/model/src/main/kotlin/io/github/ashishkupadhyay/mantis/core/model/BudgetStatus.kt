package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.LocalDate

/**
 * One occurrence of a budget's period. [key] names it for snooze and alert dedupe: `2026-09` (monthly, the month
 * the period starts in), `W2026-09-07` (weekly, start date), `2026` (yearly) or `custom` (a one-off range).
 */
data class BudgetPeriod(val range: DateRange, val key: String) {
    val start: LocalDate get() = range.start
    val endInclusive: LocalDate get() = range.endInclusive
    val days: Int get() = range.days.toInt()
}

/** Pace classification (doc 02 §6.2): projected / limit ≤ 1.0 → on track, ≤ 1.10 → watch, else over. */
enum class BudgetPace { ON_TRACK, WATCH, OVER }

/**
 * What a budget looks like right now (FR-BUD-3). Amounts are positive magnitudes in the budget's currency;
 * [limit] already includes any rollover from the previous period. [projected] is the period-end estimate the pace
 * is judged on — a linear day-rate extrapolation until `PaceForecaster` (M2) replaces it, hence [lowConfidence].
 */
data class BudgetStatus(
    val budget: Budget,
    val period: BudgetPeriod,
    val spent: Money,
    val limit: Money,
    val rollover: Money,
    val projected: Money,
    /** Days elapsed including today, clamped to the period. */
    val elapsedDays: Int,
    /** Whole days after today up to and including the period end; 0 on the last day (or after a custom range ends). */
    val daysLeft: Int,
    val pace: BudgetPace,
    val lowConfidence: Boolean = true,
) {
    val remaining: Money get() = limit - spent
    val isOver: Boolean get() = spent > limit
    val isSnoozed: Boolean get() = budget.snoozedUntilPeriodKey == period.key

    /** Spent as a fraction of the limit (1.0 = 100 %); may exceed 1. */
    val fractionUsed: Float get() = fraction(spent)
    val projectedFraction: Float get() = fraction(projected)

    /** Where "today" sits in the period, 0..1. */
    val periodFraction: Float get() = (elapsedDays.toFloat() / period.days.coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Whole percent used, rounded half up — 84 for 83.75 % — as notifications and tiles quote it. */
    val percentUsed: Int get() = if (limit.minor <= 0) 0 else ((spent.minor * PERCENT + limit.minor / 2) / limit.minor).toInt()

    /** Exact threshold test: 83.75 % has reached 80 but not 84 (thresholds never round up into firing). */
    fun hasReached(percent: Int): Boolean = limit.minor > 0 && spent.minor * PERCENT >= limit.minor * percent

    private fun fraction(money: Money): Float = if (limit.minor <= 0) 0f else money.minor.toFloat() / limit.minor.toFloat()

    private companion object {
        const val PERCENT = 100L
    }
}

/** A finished (or current) period in the budget's history (FR-BUD-5): what was spent against what was allowed. */
data class BudgetPeriodSummary(val period: BudgetPeriod, val spent: Money, val limit: Money) {
    val isOver: Boolean get() = spent > limit
}
