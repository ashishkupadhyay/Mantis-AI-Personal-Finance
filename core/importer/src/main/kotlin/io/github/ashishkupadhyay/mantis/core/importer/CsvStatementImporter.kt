package io.github.ashishkupadhyay.mantis.core.importer

import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreset
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.SniffResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.StatementImporter
import io.github.ashishkupadhyay.mantis.core.importer.csv.CsvReader
import io.github.ashishkupadhyay.mantis.core.importer.csv.CsvSniffer
import io.github.ashishkupadhyay.mantis.core.importer.parse.AmountParser
import io.github.ashishkupadhyay.mantis.core.importer.parse.DateParser
import io.github.ashishkupadhyay.mantis.core.importer.preset.BundledPresets
import io.github.ashishkupadhyay.mantis.core.importer.preset.MappingInference
import io.github.ashishkupadhyay.mantis.core.importer.preset.PresetMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import kotlin.math.abs

/** Doc 02 §7 steps 1–4 for CSV files: sniff → preset/mapping → stream rows → derive signed amounts and dates. */
class CsvStatementImporter @Inject constructor() : StatementImporter {

    override fun presets(): List<ImportPreset> = BundledPresets.all

    override fun dateFormatsFor(samples: List<String>): List<String> = DateParser.infer(samples)

    override suspend fun sniff(source: ImportSource): Outcome<SniffResult> = withContext(Dispatchers.IO) {
        if (source.sizeBytes > MAX_BYTES) {
            return@withContext Outcome.failure(AppError.Validation("file", "Files over 50 MB are not supported"))
        }
        val sample = try {
            CsvSniffer.readSample(source.bytes.open())
        } catch (e: IOException) {
            return@withContext Outcome.failure(AppError.Io("Could not read the file", e))
        }
        if (sample.isEmpty()) return@withContext Outcome.failure(AppError.Validation("file", "The file is empty"))
        val sniff = CsvSniffer.sniff(sample)
        val sampleRows = mutableListOf<List<String>>()
        val text = sniff.text
        CsvReader(sniff.delimiter).read(text.reader()) { line, cells ->
            if (line > sniff.headerRow && sampleRows.size < SAMPLE_ROWS && cells.any { it.isNotBlank() }) {
                sampleRows += cells.map { it.trim() }
            }
        }
        val preset = PresetMatcher.match(sniff.headerCells)
        val dateIndex = preset?.mapping?.indexOf(ColumnRole.DATE)
            ?: MappingInference.infer(sniff.headerCells, "").indexOf(ColumnRole.DATE)
        val dateFormats = dateIndex?.let { index -> DateParser.infer(sampleRows.mapNotNull { it.getOrNull(index) }) }.orEmpty()
        val suggested = preset?.mapping?.let { mapping ->
            // A preset names the format, but the file wins if its sample rows disagree.
            if (dateFormats.isEmpty() || mapping.dateFormat in dateFormats) mapping else mapping.copy(dateFormat = dateFormats.first())
        } ?: MappingInference.infer(sniff.headerCells, dateFormats.firstOrNull() ?: DateParser.CANDIDATES.first())
        Outcome.success(
            SniffResult(
                encoding = sniff.charset.name(),
                delimiter = sniff.delimiter,
                headerRow = sniff.headerRow,
                headers = sniff.headerCells,
                sampleRows = sampleRows,
                preset = preset,
                suggestedMapping = suggested,
                suggestedDateFormats = dateFormats,
            ),
        )
    }

    override suspend fun parse(source: ImportSource, sniff: SniffResult, mapping: ColumnMapping): Outcome<List<ParsedRow>> =
        withContext(Dispatchers.IO) {
            if (!mapping.isComplete) {
                return@withContext Outcome.failure(AppError.Validation("mapping", "Map the date, description and amount columns"))
            }
            val rows = mutableListOf<ParsedRow>()
            try {
                InputStreamReader(source.bytes.open(), charset(sniff.encoding)).use { reader ->
                    CsvReader(sniff.delimiter).read(reader) { line, cells ->
                        if (line > sniff.headerRow && cells.any { it.isNotBlank() }) {
                            rows += derive(line, cells.map { it.trim() }, mapping)
                        }
                    }
                }
            } catch (e: IOException) {
                return@withContext Outcome.failure(AppError.Io("Could not read the file", e))
            }
            Outcome.success(rows)
        }

    /** One record → a [ParsedRow]; problems become [ParsedRow.error] so the preview can show them (FR-IMP-5). */
    internal fun derive(line: Int, cells: List<String>, mapping: ColumnMapping): ParsedRow {
        fun cell(role: ColumnRole): String? = mapping.indexOf(role)?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
        val dateText = cell(ColumnRole.DATE)
        val description = cell(ColumnRole.DESCRIPTION).orEmpty()
        val date = dateText?.let { DateParser.parse(it, mapping.dateFormat) }
        val amount = signedAmount(mapping, ::cell)
        val error = when {
            dateText == null && amount == null && description.isEmpty() -> "Empty row"
            dateText == null -> "Missing date"
            date == null -> "Unreadable date “$dateText”"
            amount == null -> "Missing amount"
            amount == 0L -> "Zero amount"
            else -> null
        }
        return ParsedRow(
            line = line,
            raw = cells,
            date = date,
            description = description,
            amountMinor = amount,
            reference = cell(ColumnRole.REFERENCE),
            categoryName = cell(ColumnRole.CATEGORY),
            error = error,
        )
    }

    private fun signedAmount(mapping: ColumnMapping, cell: (ColumnRole) -> String?): Long? {
        val debit = cell(ColumnRole.DEBIT)?.let(AmountParser::parseMinor)
        val credit = cell(ColumnRole.CREDIT)?.let(AmountParser::parseMinor)
        if (mapping.indexOf(ColumnRole.DEBIT) != null || mapping.indexOf(ColumnRole.CREDIT) != null) {
            return when {
                debit != null && debit != 0L -> -abs(debit)
                credit != null -> abs(credit)
                debit != null -> 0L
                else -> null
            }
        }
        val amount = cell(ColumnRole.AMOUNT)?.let(AmountParser::parseMinor) ?: return null
        val indicator = cell(ColumnRole.DR_CR)?.lowercase()
        return when {
            indicator != null && indicator.startsWith("dr") -> -abs(amount)
            indicator != null && indicator.startsWith("cr") -> abs(amount)
            mapping.amountNegativeIsExpense -> amount
            else -> -amount
        }
    }

    private companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        const val SAMPLE_ROWS = 20
    }
}
