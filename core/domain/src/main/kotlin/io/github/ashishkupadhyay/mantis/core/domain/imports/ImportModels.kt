package io.github.ashishkupadhyay.mantis.core.domain.imports

import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import java.io.InputStream
import java.time.LocalDate

/** Where the bytes come from; opened fresh for every pass so nothing has to be held in memory (NFR-20f). */
fun interface ByteSource {
    fun open(): InputStream
}

/** A file the user picked or shared (FR-IMP-1). */
data class ImportSource(val name: String, val sizeBytes: Long, val bytes: ByteSource)

/** What a column means (FR-IMP-4). [DR_CR] is the "Dr/Cr" indicator some banks print beside an unsigned amount. */
enum class ColumnRole { DATE, DESCRIPTION, DEBIT, CREDIT, AMOUNT, DR_CR, BALANCE, REFERENCE, CATEGORY, IGNORE }

/**
 * Column roles by index plus the date pattern (`java.time` syntax). [amountNegativeIsExpense] applies to a single
 * signed `AMOUNT` column; with `DEBIT`/`CREDIT` columns the sign is implied.
 */
data class ColumnMapping(
    val roles: Map<Int, ColumnRole>,
    val dateFormat: String,
    val amountNegativeIsExpense: Boolean = true,
) {
    fun indexOf(role: ColumnRole): Int? = roles.entries.firstOrNull { it.value == role }?.key

    val hasDate: Boolean get() = indexOf(ColumnRole.DATE) != null
    val hasDescription: Boolean get() = indexOf(ColumnRole.DESCRIPTION) != null
    val hasAmount: Boolean
        get() = indexOf(ColumnRole.AMOUNT) != null || indexOf(ColumnRole.DEBIT) != null || indexOf(ColumnRole.CREDIT) != null

    val isComplete: Boolean get() = hasDate && hasDescription && hasAmount
}

/** A known bank layout (FR-IMP-3): matched on the normalised header tokens. */
data class ImportPreset(
    val id: String,
    val name: String,
    /** Normalised header cells the preset expects, in column order. */
    val headerSignature: List<String>,
    val mapping: ColumnMapping,
    val isBundled: Boolean = true,
)

/** Everything the sniffer learned about the file before any row is parsed (FR-IMP-2/3). */
data class SniffResult(
    val encoding: String,
    val delimiter: Char,
    /** Zero-based line index of the header row (bank exports often have preamble lines). */
    val headerRow: Int,
    val headers: List<String>,
    /** The first data rows, split into cells, for the mapping table and date inference. */
    val sampleRows: List<List<String>>,
    val preset: ImportPreset?,
    /** Best-guess mapping: the preset's, or one inferred from header keywords. */
    val suggestedMapping: ColumnMapping,
    val suggestedDateFormats: List<String>,
)

/** One parsed line. [error] set = the row is skipped; [amountMinor] is signed (negative = money out). */
data class ParsedRow(
    val line: Int,
    val raw: List<String>,
    val date: LocalDate? = null,
    val description: String = "",
    val amountMinor: Long? = null,
    val reference: String? = null,
    val categoryName: String? = null,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null && date != null && amountMinor != null
}

/** How a valid row will be treated once the account is known (FR-IMP-5/6). */
enum class RowDisposition { IMPORT, DUPLICATE, ERROR }

data class PreviewRow(val row: ParsedRow, val disposition: RowDisposition, val duplicateOf: TransactionId? = null)

/** Counts for the preview tiles; [willImport] honours the "import duplicates anyway" toggle. */
data class ImportPreview(
    val rows: List<PreviewRow>,
    val total: Int,
    val valid: Int,
    val duplicates: Int,
    val errors: Int,
    val willImport: Int,
)

/** Outcome of a committed batch (FR-IMP-7/8). */
data class ImportResult(
    val batchId: ImportBatchId,
    val imported: Int,
    val duplicates: Int,
    val errors: Int,
    val categorized: Int,
    val needsReview: Int,
)

/** A row ready to insert: parsed, deduped, categorized. */
data class ImportRow(
    val row: ParsedRow,
    val categoryId: CategoryId?,
    val fingerprint: String,
)

/** What the wizard hands to the committing use-case. */
data class ImportRequest(
    val source: ImportSource,
    val accountId: AccountId,
    val presetId: String?,
    val rows: List<ParsedRow>,
    val importDuplicates: Boolean,
)
