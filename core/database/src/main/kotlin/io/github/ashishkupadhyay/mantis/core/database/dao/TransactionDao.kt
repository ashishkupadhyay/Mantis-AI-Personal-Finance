package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.Relation
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import io.github.ashishkupadhyay.mantis.core.database.entity.TagEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionFtsEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionSplitEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionTagCrossRef
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** A transaction with its splits and tags, loaded in one Room transaction. */
data class TransactionWithRelations(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["transactionId"])
    val splits: List<TransactionSplitEntity>,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(TransactionTagCrossRef::class, parentColumns = ["transactionId"], entityColumns = ["tagId"]),
    )
    val tags: List<TagEntity>,
)

/** One row of a per-category aggregation (doc 02 §5.3). */
data class CategorySpendRow(val categoryId: String?, val totalMinor: Long, val count: Int)

/** Signed total and row count for one calendar day (day headers in the list, cumulative curves). */
data class DayTotalRow(val day: String, val totalMinor: Long, val count: Int)

/** Σ live posted amounts per account. */
data class AccountDeltaRow(val accountId: String, val deltaMinor: Long)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface TransactionDao {

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeWithRelations(id: String): Flow<TransactionWithRelations?>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun withRelations(ids: List<String>): List<TransactionWithRelations>

    /** Default list order: newest first, id as the tiebreaker so paging keys are stable (doc 02 §5.3). */
    @Transaction
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY postedAt DESC, id DESC")
    fun pagedAll(): PagingSource<Int, TransactionWithRelations>

    /**
     * Filtered/sorted lists are built by the repository's query builder with bound parameters, never string
     * concatenation (NFR-20c). Relations load per page inside one transaction.
     */
    @Transaction
    @RawQuery(observedEntities = [TransactionEntity::class, TransactionFtsEntity::class, TransactionTagCrossRef::class])
    fun pagedRaw(query: RoomRawQuery): PagingSource<Int, TransactionWithRelations>

    /** Bounded list for batch jobs (rule application); same builder as [pagedRaw] with a `LIMIT`. */
    @Transaction
    @RawQuery(observedEntities = [TransactionEntity::class])
    suspend fun listRaw(query: RoomRawQuery): List<TransactionWithRelations>

    /** Day totals for the same filter as [pagedRaw]; the query builder supplies the aggregation SQL. */
    @RawQuery(observedEntities = [TransactionEntity::class, TransactionFtsEntity::class, TransactionTagCrossRef::class])
    fun observeDayTotalsRaw(query: RoomRawQuery): Flow<List<DayTotalRow>>

    @Query(
        """
        SELECT categoryId FROM transactions
        WHERE type = :type AND categoryId IS NOT NULL AND deletedAt IS NULL AND postedLocalDate >= :since
        GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT :limit
        """,
    )
    suspend fun frequentCategories(type: TransactionType, since: String, limit: Int): List<String>

    /** [ftsQuery] must already be tokenised and escaped by the repository (NFR-20c). */
    @Query(
        """
        SELECT transactions.* FROM transactions
        JOIN transactions_fts ON transactions.rowid = transactions_fts.rowid
        WHERE transactions_fts MATCH :ftsQuery AND transactions.deletedAt IS NULL
        ORDER BY transactions.postedAt DESC, transactions.id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(ftsQuery: String, limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND fingerprint IN (:fingerprints)")
    suspend fun byFingerprints(accountId: String, fingerprints: List<String>): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
        ORDER BY postedAt DESC, id DESC
        """,
    )
    suspend fun inRange(accountId: String, from: String, to: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE importBatchId = :batchId")
    suspend fun byImportBatch(batchId: String): List<TransactionEntity>

    @Query("UPDATE transactions SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL, dirty = 1, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long): Int

    @Query("UPDATE transactions SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDeleteAll(ids: List<String>, at: Long): Int

    @Query("UPDATE transactions SET deletedAt = NULL, dirty = 1, updatedAt = :at WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun restoreAll(ids: List<String>, at: Long): Int

    @Query("UPDATE transactions SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteImportBatch(batchId: String, at: Long): Int

    /** Tombstones older than [before] that the server has acknowledged (or, in local-only mode, any tombstone). */
    @Query("DELETE FROM transactions WHERE deletedAt IS NOT NULL AND deletedAt < :before AND (dirty = 0 OR :localOnly)")
    suspend fun purgeTombstones(before: Long, localOnly: Boolean): Int

    @Query(
        """
        SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
        GROUP BY categoryId
        """,
    )
    fun observeSpendByCategory(from: String, to: String): Flow<List<CategorySpendRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
        """,
    )
    fun observeSpendTotal(from: String, to: String): Flow<Long>

    @Query(
        """
        SELECT postedLocalDate AS day, SUM(amountMinor) AS totalMinor, COUNT(*) AS count FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
        GROUP BY postedLocalDate ORDER BY postedLocalDate
        """,
    )
    suspend fun dailySpend(from: String, to: String): List<DayTotalRow>

    // --- budget scopes: expenses in a range, optionally narrowed to categories / accounts (FR-BUD-1/3) ----------

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
          AND (:allCategories OR categoryId IN (:categoryIds)) AND (:allAccounts OR accountId IN (:accountIds))
        """,
    )
    fun observeScopedSpend(
        from: String,
        to: String,
        allCategories: Boolean,
        categoryIds: List<String>,
        allAccounts: Boolean,
        accountIds: List<String>,
    ): Flow<Long>

    @Query(
        """
        SELECT postedLocalDate AS day, SUM(amountMinor) AS totalMinor, COUNT(*) AS count FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
          AND (:allCategories OR categoryId IN (:categoryIds)) AND (:allAccounts OR accountId IN (:accountIds))
        GROUP BY postedLocalDate ORDER BY postedLocalDate
        """,
    )
    suspend fun scopedDailySpend(
        from: String,
        to: String,
        allCategories: Boolean,
        categoryIds: List<String>,
        allAccounts: Boolean,
        accountIds: List<String>,
    ): List<DayTotalRow>

    @Query(
        """
        SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count FROM transactions
        WHERE type = 'EXPENSE' AND isExcluded = 0 AND deletedAt IS NULL AND postedLocalDate BETWEEN :from AND :to
          AND (:allCategories OR categoryId IN (:categoryIds)) AND (:allAccounts OR accountId IN (:accountIds))
        GROUP BY categoryId ORDER BY SUM(amountMinor)
        """,
    )
    suspend fun scopedSpendByCategory(
        from: String,
        to: String,
        allCategories: Boolean,
        categoryIds: List<String>,
        allAccounts: Boolean,
        accountIds: List<String>,
    ): List<CategorySpendRow>

    /** Balance contribution of an account: Σ signed amounts of live, posted rows (opening balance is added by the caller). */
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND deletedAt IS NULL AND isPending = 0")
    fun observeBalanceDelta(accountId: String): Flow<Long>

    @Query(
        "SELECT accountId, SUM(amountMinor) AS deltaMinor FROM transactions " +
            "WHERE deletedAt IS NULL AND isPending = 0 GROUP BY accountId",
    )
    fun observeBalanceDeltas(): Flow<List<AccountDeltaRow>>

    @Query("UPDATE transactions SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE accountId = :accountId AND deletedAt IS NULL")
    suspend fun softDeleteByAccount(accountId: String, at: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<TransactionEntity>

    // --- splits and tags -------------------------------------------------------------------------------------

    @Upsert
    suspend fun upsertSplits(splits: List<TransactionSplitEntity>)

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId")
    suspend fun deleteSplits(transactionId: String)

    @Upsert
    suspend fun upsertTagRefs(refs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun deleteTagRefs(transactionId: String)

    /** Writes a transaction and replaces its splits and tags atomically. */
    @Transaction
    suspend fun replace(transaction: TransactionEntity, splits: List<TransactionSplitEntity>, tagIds: Set<String>) {
        upsert(transaction)
        deleteSplits(transaction.id)
        if (splits.isNotEmpty()) upsertSplits(splits)
        deleteTagRefs(transaction.id)
        if (tagIds.isNotEmpty()) upsertTagRefs(tagIds.map { TransactionTagCrossRef(transaction.id, it) })
    }
}
