package io.github.ashishkupadhyay.mantis.core.domain.transaction

import io.github.ashishkupadhyay.mantis.core.common.log.Logger
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import javax.inject.Inject

/**
 * Hook run after transactions are written through the use-cases (doc 02 §4.1: "insert + categorize + budget check +
 * notification"). Budgets (WP-1.6) evaluate thresholds, insights (M2) refresh — each contributes one observer into
 * the Hilt set, so the use-case never learns about them. Observers run after the write commits.
 */
fun interface TransactionWriteObserver {
    suspend fun onWritten(transactions: List<Transaction>)
}

/** Fans a write out to every observer; one failing observer is logged and never blocks the others or the caller. */
class TransactionWriteObservers @Inject constructor(
    private val observers: Set<@JvmSuppressWildcards TransactionWriteObserver>,
    private val logger: Logger,
) {
    suspend fun notify(written: List<Transaction>) {
        observers.forEach { observer ->
            Outcome.runCatching { observer.onWritten(written) }
                .onFailure { logger.w(TAG, it.cause) { "write observer failed: ${it.message}" } }
        }
    }

    private companion object {
        const val TAG = "TransactionWrite"
    }
}
