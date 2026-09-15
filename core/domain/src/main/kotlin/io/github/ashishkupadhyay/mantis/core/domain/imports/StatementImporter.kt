package io.github.ashishkupadhyay.mantis.core.domain.imports

import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The parsing half of the import subsystem (doc 02 §7 steps 1–4), implemented in `core:importer`. Pure: it never
 * touches the database, so the wizard can re-run it freely while the user changes the mapping.
 */
interface StatementImporter {
    /** Encoding, delimiter, header row, preset match and a suggested mapping (FR-IMP-2/3). */
    suspend fun sniff(source: ImportSource): Outcome<SniffResult>

    /** Every data row under [mapping]; invalid rows come back with an [ParsedRow.error] instead of being dropped. */
    suspend fun parse(source: ImportSource, sniff: SniffResult, mapping: ColumnMapping): Outcome<List<ParsedRow>>

    /** Presets the user can pick by hand when detection fails. */
    fun presets(): List<ImportPreset>

    /** Date patterns that read every non-blank value in [samples], best first — re-run when the user re-maps the date column. */
    fun dateFormatsFor(samples: List<String>): List<String>
}

/** The database half (doc 02 §7 steps 5–8), implemented in `core:data`. */
interface ImportRepository {
    /** Fingerprint and ±1-day fuzzy duplicates of [rows] on [accountId] (FR-IMP-6). */
    suspend fun findDuplicates(accountId: AccountId, rows: List<ParsedRow>): Map<Int, Transaction>

    /** Inserts the batch row and every transaction in one write (NFR-11). */
    suspend fun commit(batch: ImportBatch, transactions: List<Transaction>)

    /** Removes exactly the rows a batch created and marks it undone (FR-IMP-8); returns how many rows went. */
    suspend fun undo(batchId: ImportBatchId): Int

    fun observeBatches(): Flow<List<ImportBatch>>

    suspend fun batch(id: ImportBatchId): ImportBatch?
}
