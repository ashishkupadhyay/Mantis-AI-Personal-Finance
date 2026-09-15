package io.github.ashishkupadhyay.mantis.core.domain.budget

import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.BudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObserver
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetStatus
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import javax.inject.Inject

/**
 * A threshold crossing worth telling the user about (FR-BUD-4). [threshold] is the percentage crossed, or
 * [Budget.PROJECTED_ALERT] when the period-end projection went past the limit.
 */
data class BudgetAlert(val status: BudgetStatus, val threshold: Int) {
    val isProjected: Boolean get() = threshold == Budget.PROJECTED_ALERT
}

/** Posts a budget alert to the user; `core:notifications` binds the local-notification implementation (FR-NTF-1). */
fun interface BudgetAlertNotifier {
    suspend fun notify(alert: BudgetAlert)
}

/**
 * Evaluates budget thresholds right after transactions are written, on device, offline (FR-BUD-4, FR-NTF-1).
 * Only budgets whose scope covers one of the written rows are re-evaluated. Each (budget, period, threshold) fires
 * once — [BudgetRepository.markAlerted] is the dedupe — and when one write jumps several thresholds only the highest
 * is announced. A budget snoozed for the period stays quiet (FR-NTF-5). The projected-overspend alert waits until
 * a week of the period has passed so a heavy first day does not cry wolf.
 */
class BudgetAlertObserver @Inject constructor(
    private val budgets: BudgetRepository,
    private val engine: BudgetEngine,
    private val notifier: BudgetAlertNotifier,
    private val clock: Clock,
) : TransactionWriteObserver {

    override suspend fun onWritten(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        val today = clock.today()
        val periods = engine.periods()
        budgets.budgets()
            .filter { budget -> budget.snoozedUntilPeriodKey != periods.current(budget, today).key }
            .filter { budget -> engine.scope(budget, periods.current(budget, today)).let { scope -> transactions.any(scope::covers) } }
            .forEach { budget -> evaluate(engine.status(budget, today)) }
    }

    /** Checks one status against its thresholds and notifies what is newly crossed. */
    suspend fun evaluate(status: BudgetStatus) {
        val budget = status.budget
        val key = status.period.key
        val crossed = budget.thresholds.filter(status::hasReached)
        val fresh = crossed.filter { budgets.markAlerted(budget.id, key, it) }
        fresh.maxOrNull()?.let { notifier.notify(BudgetAlert(status, it)) }

        val projectedOver = budget.alertOnProjectedOverspend && !status.isOver && status.projected > status.limit &&
            status.elapsedDays >= PROJECTION_MIN_DAYS
        if (projectedOver && budgets.markAlerted(budget.id, key, Budget.PROJECTED_ALERT)) {
            notifier.notify(BudgetAlert(status, Budget.PROJECTED_ALERT))
        }
    }

    private companion object {
        const val PROJECTION_MIN_DAYS = 7
    }
}
