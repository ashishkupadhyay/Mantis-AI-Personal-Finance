package io.github.ashishkupadhyay.mantis.core.data.repository

import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.data.mapper.splitEntities
import io.github.ashishkupadhyay.mantis.core.data.mapper.toEntity
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stamps and writes transactions with their splits, tags and outbox entries. Shared by the transaction and import
 * repositories so both write the same way; callers open the write transaction (`db.write { … }`).
 */
@Singleton
class TransactionWriter @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
    private val clock: Clock,
) {
    suspend fun write(transactions: List<Transaction>) {
        val offsetMinutes = clock.zone.rules.getOffset(clock.now()).totalSeconds / SECONDS_PER_MINUTE
        val dao = db.transactionDao()
        transactions.forEach { transaction ->
            val stamped = transaction.copy(meta = stamper.stamp(transaction.meta))
            dao.replace(stamped.toEntity(offsetMinutes), stamped.splitEntities(), stamped.tags.map { it.value }.toSet())
            outbox.upserted(OutboxWriter.TRANSACTIONS, stamped.id.value)
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
