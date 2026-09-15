package io.github.ashishkupadhyay.mantis.core.data.repository

import io.github.ashishkupadhyay.mantis.core.data.mapper.toEntity
import io.github.ashishkupadhyay.mantis.core.data.mapper.toModel
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRepository
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Doc 02 §7 steps 5, 7 and 8: dedupe against what is already on the account, commit atomically, undo by batch. */
@Singleton
class DefaultImportRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
    private val writer: TransactionWriter,
) : ImportRepository {

    private val imports get() = db.importDao()
    private val transactions get() = db.transactionDao()

    /**
     * Exact fingerprint hits, then the fuzzy pass: same signed amount and normalised description within ±1 day,
     * which absorbs value-date vs transaction-date drift between two exports of the same statement (FR-IMP-6).
     */
    override suspend fun findDuplicates(accountId: AccountId, rows: List<ParsedRow>): Map<Int, Transaction> {
        val valid = rows.withIndex().filter { it.value.isValid }
        if (valid.isEmpty()) return emptyMap()
        val dates = valid.map { checkNotNull(it.value.date) }
        val existing = transactions.inRange(accountId.value, dates.min().minusDays(1).toString(), dates.max().plusDays(1).toString())
            .map { it.toModel() }
        if (existing.isEmpty()) return emptyMap()
        val byFingerprint = existing.associateBy { it.fingerprint }
        val byAmount = existing.groupBy { it.amount.minor }
        return buildMap {
            valid.forEach { (index, row) ->
                val date = checkNotNull(row.date)
                val minor = checkNotNull(row.amountMinor)
                val exact = byFingerprint[TransactionFingerprint.of(accountId, date, minor, row.description)]
                val fuzzy = exact ?: byAmount[minor]?.firstOrNull { candidate ->
                    normalise(candidate.descriptionRaw) == normalise(row.description) &&
                        kotlin.math.abs(candidate.postedLocalDate.toEpochDay() - date.toEpochDay()) <= 1
                }
                if (fuzzy != null) put(index, fuzzy)
            }
        }
    }

    override suspend fun commit(batch: ImportBatch, transactions: List<Transaction>) = db.write {
        imports.upsertBatch(batch.toEntity())
        writer.write(transactions)
    }

    override suspend fun undo(batchId: ImportBatchId): Int = db.write {
        val at = stamper.now()
        val ids = transactions.byImportBatch(batchId.value).filter { it.sync.deletedAt == null }.map { it.id }
        val removed = transactions.softDeleteImportBatch(batchId.value, at)
        ids.forEach { outbox.deleted(OutboxWriter.TRANSACTIONS, it) }
        imports.markUndone(batchId.value, at)
        removed
    }

    override fun observeBatches(): Flow<List<ImportBatch>> = imports.observeBatches().map { rows -> rows.map { it.toModel() } }

    override suspend fun batch(id: ImportBatchId): ImportBatch? = imports.getBatch(id.value)?.toModel()

    private fun normalise(text: String): String = text.lowercase().replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
