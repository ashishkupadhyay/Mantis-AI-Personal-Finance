package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts4
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.EntrySource
import io.github.ashishkupadhyay.mantis.core.model.SplitOrigin
import io.github.ashishkupadhyay.mantis.core.model.TransactionType

/**
 * `transactions` (doc 02 §5.2). `amountMinor` is signed: negative = money out. `postedLocalDate` (ISO date in the
 * user's zone at write time) drives every period query, so the indexes pair it with the grouping columns.
 *
 * `(accountId, fingerprint)` is indexed but deliberately **not** unique: statements legitimately contain identical
 * rows (two metro rides), and duplicate detection is the importer's fuzzy ±1-day rule (FR-IMP-5), not a constraint.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT, deferred = true),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL, deferred = true),
        ForeignKey(MerchantEntity::class, ["id"], ["merchantId"], onDelete = ForeignKey.SET_NULL, deferred = true),
        ForeignKey(ImportBatchEntity::class, ["id"], ["importBatchId"], onDelete = ForeignKey.SET_NULL, deferred = true),
    ],
    indices = [
        Index("accountId", "postedAt"),
        Index("accountId", "postedLocalDate"),
        Index("categoryId", "postedLocalDate"),
        Index("merchantId", "postedLocalDate"),
        Index("accountId", "fingerprint"),
        Index("importBatchId"),
        Index("transferPairId"),
        Index("recurringSeriesId"),
        Index("postedAt", "id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val postedAt: Long,
    val postedLocalDate: String,
    val tzOffsetMinutes: Int,
    val descriptionRaw: String,
    val merchantNormalized: String?,
    val merchantId: String?,
    val categoryId: String?,
    val categorySource: CategorySource,
    val categoryConfidence: Float?,
    val notes: String?,
    val isExcluded: Boolean,
    val isPending: Boolean,
    val needsReview: Boolean,
    val transferPairId: String?,
    val recurringSeriesId: String?,
    val importBatchId: String?,
    val fingerprint: String,
    val anomalyScore: Float?,
    val entrySource: EntrySource,
    @Embedded val sync: SyncColumns,
)

/**
 * External-content FTS4 shadow of `transactions` (NFR-3): Room's generated triggers keep it in sync by `rowid`.
 * Because `transactions` has a TEXT primary key its rowid is not guaranteed stable across `VACUUM`, so the
 * maintenance worker issues `INSERT INTO transactions_fts(transactions_fts) VALUES('rebuild')` after vacuuming.
 */
@Fts4(contentEntity = TransactionEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "transactions_fts")
data class TransactionFtsEntity(
    val descriptionRaw: String,
    val merchantNormalized: String?,
    val notes: String?,
)

/** `transaction_splits` (FR-TXN-6): Σ splits == parent amount, enforced by the domain model, not the schema. */
@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT, deferred = true),
    ],
    indices = [Index("transactionId"), Index("categoryId")],
)
data class TransactionSplitEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val categoryId: String,
    val amountMinor: Long,
    val note: String?,
    val origin: SplitOrigin,
    val sortOrder: Int,
)

/** `transaction_tags` join table (FR-VIEW-4). */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TransactionTagCrossRef(
    val transactionId: String,
    val tagId: String,
)
