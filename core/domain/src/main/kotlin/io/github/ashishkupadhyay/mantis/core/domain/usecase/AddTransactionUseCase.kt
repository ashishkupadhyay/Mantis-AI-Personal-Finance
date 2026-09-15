package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationRequest
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.EntrySource
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.time.Instant
import javax.inject.Inject

/**
 * What the add/edit sheet (and later widgets, the assistant and receipt review) hands over. [amount] is the
 * magnitude the user typed; the sign follows [type]. A transfer names both accounts and becomes two linked rows.
 * [id] set = edit that transaction in place.
 */
data class TransactionDraft(
    val type: TransactionType,
    val amount: Money,
    val accountId: AccountId,
    val postedAt: Instant,
    val description: String,
    val id: TransactionId? = null,
    val toAccountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val notes: String? = null,
    val tagNames: List<String> = emptyList(),
    val isExcluded: Boolean = false,
    val entrySource: EntrySource = EntrySource.MANUAL,
)

/**
 * Insert or edit a transaction (FR-TXN-1/2, doc 02 §4.1): validate → sign the amount → resolve the category (the
 * user's choice wins, otherwise the [CategorizationPipeline], otherwise uncategorized) → fingerprint → tags →
 * one atomic write (repository stamps sync columns and the outbox) → [TransactionWriteObservers] (budgets,
 * insights). Transfers are written as a linked pair on both accounts (FR-ACC-4).
 */
class AddTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val pipeline: CategorizationPipeline,
    private val observers: TransactionWriteObservers,
    private val clock: Clock,
    private val ids: UuidV7,
) {

    suspend operator fun invoke(draft: TransactionDraft): Outcome<TransactionId> {
        validate(draft)?.let { return Outcome.failure(it) }
        val existing = draft.id?.let { id ->
            transactions.getTransaction(id) ?: return Outcome.failure(AppError.NotFound("Transaction", id.value))
        }
        val rows = if (draft.type == TransactionType.TRANSFER) transferPair(draft, existing) else listOf(single(draft, existing))
        transactions.saveAll(rows)
        observers.notify(rows)
        return Outcome.success(rows.first().id)
    }

    /** Existing rows a manual entry may duplicate (FR-TXN-11); empty for edits. */
    suspend fun duplicatesOf(draft: TransactionDraft): List<Transaction> {
        if (draft.id != null || validate(draft) != null) return emptyList()
        val candidate = if (draft.type == TransactionType.TRANSFER) transferPair(draft, null).first() else single(draft, null)
        return transactions.findDuplicates(candidate)
    }

    private fun validate(draft: TransactionDraft): AppError? = when {
        draft.amount.minor == 0L -> AppError.Validation("amount", "Enter an amount")
        draft.type == TransactionType.TRANSFER && draft.toAccountId == null ->
            AppError.Validation("toAccount", "Choose the account the money goes to")
        draft.type == TransactionType.TRANSFER && draft.toAccountId == draft.accountId ->
            AppError.Validation("toAccount", "Transfer between two different accounts")
        else -> null
    }

    private suspend fun single(draft: TransactionDraft, existing: Transaction?): Transaction {
        val magnitude = draft.amount.abs()
        val amount = if (draft.type == TransactionType.EXPENSE) -magnitude else magnitude
        val label = resolveCategory(draft, existing, amount)
        return build(draft, existing, id = existing?.id ?: TransactionId(ids.nextString()), amount = amount, label = label)
    }

    /** Two rows: money out of [TransactionDraft.accountId], money into [TransactionDraft.toAccountId]; each points at the other. */
    private suspend fun transferPair(draft: TransactionDraft, existing: Transaction?): List<Transaction> {
        val magnitude = draft.amount.abs()
        val transfer = categories.byKey(DefaultTaxonomy.KEY_TRANSFER)?.id
        val label = CategoryLabel(transfer, if (transfer != null) CategorySource.USER else CategorySource.NONE, null, false)
        val pair = existing?.transferPairId?.let { transactions.getTransaction(it) }
        val outId = existing?.id ?: TransactionId(ids.nextString())
        val inId = pair?.id ?: TransactionId(ids.nextString())
        // Editing the "in" leg keeps this row as the receiving side: the sign of the existing row decides.
        val thisIsOut = existing == null || existing.amount.minor < 0
        val out = build(
            draft, if (thisIsOut) existing else pair, id = if (thisIsOut) outId else inId, amount = -magnitude, label = label,
            accountId = draft.accountId, pairId = if (thisIsOut) inId else outId,
        )
        val inn = build(
            draft, if (thisIsOut) pair else existing, id = if (thisIsOut) inId else outId, amount = magnitude, label = label,
            accountId = checkNotNull(draft.toAccountId), pairId = if (thisIsOut) outId else inId,
        )
        return if (thisIsOut) listOf(out, inn) else listOf(inn, out)
    }

    private suspend fun resolveCategory(draft: TransactionDraft, existing: Transaction?, amount: Money): CategoryLabel {
        val chosen = draft.categoryId
        return when {
            chosen != null && existing != null && chosen == existing.categoryId ->
                CategoryLabel(chosen, existing.categorySource, existing.categoryConfidence, existing.needsReview)
            chosen != null -> CategoryLabel(chosen, CategorySource.USER, null, false)
            else -> pipeline.categorize(
                CategorizationRequest(draft.description, amount, draft.type, draft.accountId, draft.postedAt),
            )?.let { CategoryLabel(it.categoryId, it.source, it.confidence, it.needsReview) }
                ?: CategoryLabel(null, CategorySource.NONE, null, false)
        }
    }

    @Suppress("LongParameterList")
    private suspend fun build(
        draft: TransactionDraft,
        existing: Transaction?,
        id: TransactionId,
        amount: Money,
        label: CategoryLabel,
        accountId: AccountId = draft.accountId,
        pairId: TransactionId? = existing?.transferPairId,
    ): Transaction {
        // A blank narration falls back to something readable: the old text, the category, or the type.
        val description = draft.description.trim()
            .ifEmpty { existing?.descriptionRaw.orEmpty() }
            .ifEmpty { label.categoryId?.let { categories.getCategory(it)?.name }.orEmpty() }
            .ifEmpty { draft.type.name.lowercase().replaceFirstChar(Char::titlecase) }
        val date = draft.postedAt.atZone(clock.zone).toLocalDate()
        val descriptionChanged = existing != null && existing.descriptionRaw != description
        return Transaction(
            id = id,
            accountId = accountId,
            type = draft.type,
            amount = amount,
            postedAt = draft.postedAt,
            postedLocalDate = date,
            descriptionRaw = description,
            merchantNormalized = existing?.merchantNormalized?.takeUnless { descriptionChanged },
            merchantId = existing?.merchantId?.takeUnless { descriptionChanged },
            categoryId = label.categoryId,
            categorySource = label.source,
            categoryConfidence = label.confidence,
            notes = draft.notes?.trim()?.ifEmpty { null },
            isExcluded = draft.isExcluded,
            isPending = false,
            needsReview = label.needsReview,
            transferPairId = pairId,
            recurringSeriesId = existing?.recurringSeriesId,
            importBatchId = existing?.importBatchId,
            fingerprint = TransactionFingerprint.of(accountId, date, amount.minor, description),
            anomalyScore = existing?.anomalyScore,
            entrySource = existing?.entrySource ?: draft.entrySource,
            tags = resolveTags(draft.tagNames),
            splits = existing?.splits?.takeIf { it.sumOf { s -> s.amount.minor } == amount.minor }.orEmpty(),
            meta = existing?.meta ?: SyncMeta.unstamped(),
        )
    }

    private suspend fun resolveTags(names: List<String>): Set<TagId> =
        names.map(String::trim).filter { it.isNotEmpty() }.distinct().map { tags.findOrCreate(it).id }.toSet()

    private data class CategoryLabel(
        val categoryId: CategoryId?,
        val source: CategorySource,
        val confidence: Float?,
        val needsReview: Boolean,
    )
}
