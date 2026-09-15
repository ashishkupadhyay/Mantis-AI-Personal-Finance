package io.github.ashishkupadhyay.mantis.core.domain.budget

import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriod
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriodType
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Resolves a budget to the period containing a date (doc 02 §6.2, FR-BUD-2). Months start on the user's
 * [monthStartDay] (1..28, e.g. a salary day), weeks on [firstDayOfWeek]; years run January–December; a custom
 * budget is its one fixed range and has no neighbours.
 */
class BudgetPeriods(private val monthStartDay: Int, private val firstDayOfWeek: DayOfWeek) {

    fun current(budget: Budget, today: LocalDate): BudgetPeriod = containing(budget, today)

    /** The period of [budget] that [date] falls in (a custom budget always answers with its own range). */
    fun containing(budget: Budget, date: LocalDate): BudgetPeriod = when (budget.period) {
        BudgetPeriodType.MONTHLY -> {
            val anchor = if (date.dayOfMonth >= monthStartDay) date else date.minusMonths(1)
            monthly(anchor.withDayOfMonth(monthStartDay))
        }
        BudgetPeriodType.WEEKLY -> weekly(date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)))
        BudgetPeriodType.YEARLY -> yearly(date.year)
        BudgetPeriodType.CUSTOM -> BudgetPeriod(DateRange(checkNotNull(budget.customStart), checkNotNull(budget.customEnd)), CUSTOM_KEY)
    }

    /** The period right before [period], or null when the budget does not recur. */
    fun previous(budget: Budget, period: BudgetPeriod): BudgetPeriod? = when (budget.period) {
        BudgetPeriodType.MONTHLY -> monthly(period.start.minusMonths(1))
        BudgetPeriodType.WEEKLY -> weekly(period.start.minusWeeks(1))
        BudgetPeriodType.YEARLY -> yearly(period.start.year - 1)
        BudgetPeriodType.CUSTOM -> null
    }

    private fun monthly(start: LocalDate) =
        BudgetPeriod(DateRange(start, start.plusMonths(1).minusDays(1)), "%04d-%02d".format(start.year, start.monthValue))

    private fun weekly(start: LocalDate) = BudgetPeriod(DateRange(start, start.plusDays(DAYS_IN_WEEK - 1L)), "W$start")

    private fun yearly(year: Int): BudgetPeriod {
        val start = LocalDate.of(year, 1, 1)
        return BudgetPeriod(DateRange(start, start.plusYears(1).minusDays(1)), year.toString())
    }

    companion object {
        const val CUSTOM_KEY = "custom"
        private const val DAYS_IN_WEEK = 7
    }
}
