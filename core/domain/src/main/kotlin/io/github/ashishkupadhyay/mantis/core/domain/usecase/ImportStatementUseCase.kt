package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationRequest
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreview
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRepository
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRequest
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.PreviewRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.RowDisposition
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.EntrySource
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject

/**
 * Doc 02 §7 steps 5–8 for a parsed statement: dedupe (FR-IMP-6) → categorize every row through the pipeline
 * (FR-IMP-7; a `Category` column wins when its name matches) → one atomic batch (FR-IMP-8, NFR-11) → observers.
 * Re-importing the same file yields zero new rows because every row is a fingerprint duplicate.
 */
class ImportStatementUseCase @Inject constructor(
    private val imports: ImportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val pipeline: CategorizationPipeline,
    private val observers: TransactionWriteObservers,
    private val clock: Clock,
    private val ids: UuidV7,
) {

    /** Counts and per-row dispositions for the preview step (FR-IMP-5). */
    suspend fun preview(accountId: AccountId, rows: List<ParsedRow>, importDuplicates: Boolean): ImportPreview {
        val duplicates = imports.findDuplicates(accountId, rows)
        val previewRows = rows.mapIndexed { index, row ->
            when {
                !row.isValid -> PreviewRow(row, RowDisposition.ERROR)
                index in duplicates -> PreviewRow(row, RowDisposition.DUPLICATE, duplicates.getValue(index).id)
                else -> PreviewRow(row, RowDisposition.IMPORT)
            }
        }
        val valid = previewRows.count { it.disposition != RowDisposition.ERROR }
        val dupes = previewRows.count { it.disposition == RowDisposition.DUPLICATE }
        return ImportPreview(
            rows = previewRows,
            total = rows.size,
            valid = valid,
            duplicates = dupes,
            errors = rows.size - valid,
            willImport = if (importDuplicates) valid else valid - dupes,
        )
    }

    suspend fun commit(request: ImportRequest): Outcome<ImportResult> {
        val account = accounts.getAccount(request.accountId)
            ?: return Outcome.failure(AppError.NotFound("Account", request.accountId.value))
        val preview = preview(request.accountId, request.rows, request.importDuplicates)
        val toImport = preview.rows.filter {
            it.disposition == RowDisposition.IMPORT || (request.importDuplicates && it.disposition == RowDisposition.DUPLICATE)
        }
        if (toImport.isEmpty()) return Outcome.failure(AppError.Validation("rows", "Nothing new to import"))
        val batchId = ImportBatchId(ids.nextString())
        val byName = categories.categories().filter { !it.isHidden && !it.meta.isDeleted }.associateBy { it.name.lowercase() }
        val transactions = toImport.map { entry -> transaction(entry.row, account.id, account.currency, batchId, byName) }
        val batch = ImportBatch(
            id = batchId,
            accountId = account.id,
            sourceName = request.source.name,
            presetId = request.presetId,
            fileHash = hash(request.source),
            rowCount = preview.total,
            importedCount = transactions.size,
            duplicateCount = if (request.importDuplicates) 0 else preview.duplicates,
            errorCount = preview.errors,
            createdAt = clock.now(),
        )
        imports.commit(batch, transactions)
        observers.notify(transactions)
        val categorized = transactions.count { it.categoryId != null && !it.needsReview }
        return Outcome.success(
            ImportResult(
                batchId = batchId,
                imported = transactions.size,
                duplicates = batch.duplicateCount,
                errors = batch.errorCount,
                categorized = categorized,
                needsReview = transactions.size - categorized,
            ),
        )
    }

    suspend fun undo(batchId: ImportBatchId): Int = imports.undo(batchId)

    private suspend fun transaction(
        row: ParsedRow,
        accountId: AccountId,
        currency: Currency,
        batchId: ImportBatchId,
        byName: Map<String, Category>,
    ): Transaction {
        val date = checkNotNull(row.date)
        val amount = Money(checkNotNull(row.amountMinor), currency)
        val type = if (amount.minor < 0) TransactionType.EXPENSE else TransactionType.INCOME
        val postedAt = date.atTime(NOON).atZone(clock.zone).toInstant()
        val fromColumn = row.categoryName?.let { byName[it.trim().lowercase()] }
        val suggestion = if (fromColumn == null) {
            pipeline.categorize(CategorizationRequest(row.description, amount, type, accountId, postedAt))
        } else {
            null
        }
        return Transaction(
            id = TransactionId(ids.nextString()),
            accountId = accountId,
            type = type,
            amount = amount,
            postedAt = postedAt,
            postedLocalDate = date,
            descriptionRaw = row.description,
            categoryId = fromColumn?.id ?: suggestion?.categoryId,
            categorySource = when {
                fromColumn != null -> CategorySource.IMPORT
                suggestion != null -> suggestion.source
                else -> CategorySource.NONE
            },
            categoryConfidence = suggestion?.confidence,
            needsReview = suggestion?.needsReview ?: false,
            importBatchId = batchId,
            fingerprint = TransactionFingerprint.of(accountId, date, amount.minor, row.description),
            entrySource = EntrySource.IMPORT,
            meta = SyncMeta.unstamped(),
        )
    }

    /** SHA-256 of the file, streamed, so "already imported this file" can be recognised later. */
    private fun hash(source: ImportSource): String {
        val digest = MessageDigest.getInstance("SHA-256")
        source.bytes.open().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val NOON: LocalTime = LocalTime.NOON
        const val BUFFER = 64 * 1024
    }
}
