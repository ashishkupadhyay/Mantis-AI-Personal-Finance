package io.github.ashishkupadhyay.mantis.core.data.repository

import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.database.dao.OutboxDao
import io.github.ashishkupadhyay.mantis.core.database.entity.OutboxEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.OutboxOp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records every local change for the sync push queue (doc 03 §6). The engine (M4) snapshots the current row at push
 * time, so entries carry only the entity reference; repositories call this inside the same write transaction.
 */
@Singleton
class OutboxWriter @Inject constructor(private val outbox: OutboxDao, private val clock: Clock) {

    suspend fun upserted(entityType: String, entityId: String) = enqueue(entityType, entityId, OutboxOp.UPSERT)

    suspend fun deleted(entityType: String, entityId: String) = enqueue(entityType, entityId, OutboxOp.DELETE)

    private suspend fun enqueue(entityType: String, entityId: String, op: OutboxOp) {
        outbox.enqueue(
            OutboxEntity(entityType = entityType, entityId = entityId, op = op, payloadJson = "", createdAt = clock.epochMillis()),
        )
    }

    companion object {
        const val ACCOUNTS = "accounts"
        const val TRANSACTIONS = "transactions"
        const val CATEGORIES = "categories"
        const val CATEGORY_RULES = "category_rules"
        const val TAGS = "tags"
        const val BUDGETS = "budgets"
    }
}
