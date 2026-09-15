package io.github.ashishkupadhyay.mantis.core.domain.budget

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.SpendingRepository
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetPace
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriod
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriodSummary
import io.github.ashishkupadhyay.mantis.core.model.BudgetStatus
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySpend
import io.github.ashishkupadhyay.mantis.core.model.DayTotal
import io.github.ashishkupadhyay.mantis.core.model.SpendScope
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Turns a [Budget] into what the user sees (doc 02 §6.2, FR-BUD-2/3): the current period aligned to the month-start
 * day, spend inside the budget's scope, the limit after rollover, days left, a period-end projection and the pace.
 *
 * Rollover: `limit_n = amount + (limit_{n-1} − spent_{n-1})`, clamped to `[0, 2 × amount]`, computed back to the
 * period the budget started in (never further than [MAX_ROLLOVER_PERIODS]). The projection is a linear day-rate
 * extrapolation — honest but crude, so statuses are flagged low-confidence until `PaceForecaster` (M2) supplies the
 * historical-curve estimate; the first two days of a period are always "on track" unless the limit is already spent.
 */
class BudgetEngine @Inject constructor(
    private val spending: SpendingRepository,
    private val categories: CategoryRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
) {

    suspend fun periods(): BudgetPeriods {
        val prefs = preferences.preferences.first()
        return BudgetPeriods(prefs.monthStartDay, DayOfWeek.of(prefs.firstDayOfWeek))
    }

    /** A budget on a category group counts every leaf under it; leaves and "all spending" pass through unchanged. */
    suspend fun scope(budget: Budget, period: BudgetPeriod): SpendScope = resolve(budget).scope(period)

    suspend fun status(budget: Budget, today: LocalDate = clock.today()): BudgetStatus {
        val resolved = resolve(budget)
        val period = resolved.periods.current(budget, today)
        return status(resolved, period, spending.spend(resolved.scope(period)), today)
    }

    /** Live status: recomputed whenever counted spend inside the current period changes. */
    fun observeStatus(budget: Budget): Flow<BudgetStatus> = flow {
        val today = clock.today()
        val resolved = resolve(budget)
        val period = resolved.periods.current(budget, today)
        emitAll(spending.observeSpend(resolved.scope(period)).map { spent -> status(resolved, period, spent, today) })
    }

    /** Per-day counted spend of the current period, oldest first (the cumulative curve). */
    suspend fun dailySpend(budget: Budget, period: BudgetPeriod): List<DayTotal> = spending.dailySpend(scope(budget, period))

    suspend fun spendByCategory(budget: Budget, period: BudgetPeriod): List<CategorySpend> =
        spending.spendByCategory(scope(budget, period))

    /**
     * The last [count] periods ending with [period] (oldest first), each with what was spent and what was allowed
     * (FR-BUD-5). Periods before the budget started are left out.
     */
    suspend fun history(budget: Budget, period: BudgetPeriod, count: Int): List<BudgetPeriodSummary> {
        val resolved = resolve(budget)
        val chain = generateSequence(period) { resolved.periods.previous(budget, it) }
            .takeWhile { resolved.counts(it) }
            .take(count)
            .toList()
            .asReversed()
        return chain.map { p -> BudgetPeriodSummary(p, spending.spend(resolved.scope(p)), limit(resolved, p, depth = 0)) }
    }

    private suspend fun status(resolved: Resolved, period: BudgetPeriod, spent: Money, today: LocalDate): BudgetStatus {
        val budget = resolved.budget
        val limit = limit(resolved, period, depth = 0)
        val elapsed = (today.toEpochDay() - period.start.toEpochDay() + 1).toInt().coerceIn(1, period.days)
        val daysLeft = (period.endInclusive.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)
        val projected = if (elapsed >= period.days) spent else Money(spent.minor * period.days / elapsed, spent.currency)
        val pace = when {
            spent > limit -> BudgetPace.OVER
            elapsed <= EARLY_DAYS || limit.minor <= 0 -> BudgetPace.ON_TRACK
            projected.minor <= limit.minor -> BudgetPace.ON_TRACK
            projected.minor * PERCENT <= limit.minor * WATCH_LIMIT_PERCENT -> BudgetPace.WATCH
            else -> BudgetPace.OVER
        }
        return BudgetStatus(
            budget = budget,
            period = period,
            spent = spent,
            limit = limit,
            rollover = limit - budget.amount,
            projected = projected,
            elapsedDays = elapsed,
            daysLeft = daysLeft,
            pace = pace,
        )
    }

    /** The limit for [period] after rollover (the plain amount when rollover is off or this is the first period). */
    private suspend fun limit(resolved: Resolved, period: BudgetPeriod, depth: Int): Money {
        val budget = resolved.budget
        val amount = budget.amount
        if (!budget.rollover || depth >= MAX_ROLLOVER_PERIODS) return amount
        val previous = resolved.periods.previous(budget, period)?.takeIf { resolved.counts(it) } ?: return amount
        val carried = limit(resolved, previous, depth + 1) - spending.spend(resolved.scope(previous))
        return Money((amount + carried).minor.coerceIn(0L, amount.minor * ROLLOVER_CAP), amount.currency)
    }

    private suspend fun resolve(budget: Budget): Resolved {
        val categoryIds = if (budget.categoryIds.isEmpty()) {
            emptySet()
        } else {
            val all = categories.categories()
            budget.categoryIds.flatMapTo(mutableSetOf()) { id -> listOf(id) + all.filter { it.parentId == id }.map { it.id } }
        }
        return Resolved(budget, periods(), categoryIds)
    }

    private class Resolved(val budget: Budget, val periods: BudgetPeriods, private val categoryIds: Set<CategoryId>) {
        fun scope(period: BudgetPeriod) = SpendScope(period.range, categoryIds, budget.accountIds)

        /** Whether [period] is on or after the budget's first period. */
        fun counts(period: BudgetPeriod): Boolean = budget.startsOn?.let { !period.endInclusive.isBefore(it) } ?: true
    }

    private companion object {
        const val MAX_ROLLOVER_PERIODS = 24
        const val ROLLOVER_CAP = 2L
        const val EARLY_DAYS = 2
        const val PERCENT = 100L
        const val WATCH_LIMIT_PERCENT = 110L
    }
}
