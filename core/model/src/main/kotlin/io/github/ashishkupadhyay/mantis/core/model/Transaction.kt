package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

/** How the transaction entered Mantis (doc 02 §5.2). */
enum class EntrySource { MANUAL, IMPORT, CAPTURE, RECEIPT, SYSTEM_ASSISTANT, LLM_ASSISTANT_PROPOSAL, RECURRING_TEMPLATE }

enum class SplitOrigin { USER, RECEIPT_ITEMS, LLM }

/** Part of a transaction attributed to another category (FR-TXN-6); Σ splits must equal the parent amount. */
data class Split(
    val id: SplitId,
    val categoryId: CategoryId,
    val amount: Money,
    val note: String? = null,
    val origin: SplitOrigin = SplitOrigin.USER,
)

/**
 * A single money movement. `amount` is signed: negative = money out, positive = money in; `type` disambiguates
 * transfers (doc 02 §5.1). `postedLocalDate` is the user-zone calendar date at write time and drives all
 * period queries so a month never shifts when the user travels.
 */
data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val type: TransactionType,
    val amount: Money,
    val postedAt: Instant,
    val postedLocalDate: LocalDate,
    val descriptionRaw: String,
    val merchantNormalized: String? = null,
    val merchantId: MerchantId? = null,
    val categoryId: CategoryId? = null,
    val categorySource: CategorySource = CategorySource.NONE,
    val categoryConfidence: Float? = null,
    val notes: String? = null,
    val isExcluded: Boolean = false,
    val isPending: Boolean = false,
    val needsReview: Boolean = false,
    val transferPairId: TransactionId? = null,
    val recurringSeriesId: RecurringSeriesId? = null,
    val importBatchId: ImportBatchId? = null,
    val fingerprint: String,
    val anomalyScore: Float? = null,
    val entrySource: EntrySource = EntrySource.MANUAL,
    val tags: Set<TagId> = emptySet(),
    val splits: List<Split> = emptyList(),
    val meta: SyncMeta,
) {
    init {
        when (type) {
            TransactionType.EXPENSE -> require(amount.minor <= 0) { "Expense amounts are ≤ 0 (money out)" }
            TransactionType.INCOME -> require(amount.minor >= 0) { "Income amounts are ≥ 0 (money in)" }
            TransactionType.TRANSFER -> Unit
        }
        require(categoryConfidence == null || categoryConfidence in 0f..1f) { "confidence must be within 0..1" }
        if (splits.isNotEmpty()) {
            val total = splits.fold(0L) { acc, s -> acc + s.amount.minor }
            require(total == amount.minor) { "Splits (${total}) must sum to the transaction amount (${amount.minor})" }
            require(splits.all { it.amount.currency.code == amount.currency.code }) { "Split currency mismatch" }
        }
    }

    val isCategorized: Boolean get() = categoryId != null
    val isUserCategorized: Boolean get() = categorySource == CategorySource.USER || categorySource == CategorySource.USER_RULE

    /** Counts toward budgets and reports only when it is an expense that isn't excluded (FR-TXN-10, FR-ACC-3). */
    val countsAsSpending: Boolean get() = type == TransactionType.EXPENSE && !isExcluded && !meta.isDeleted
}
