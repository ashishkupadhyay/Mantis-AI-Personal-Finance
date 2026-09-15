package io.github.ashishkupadhyay.mantis.core.domain.fakes

import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.imports.ByteSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreset
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.SniffResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.StatementImporter
import java.io.ByteArrayInputStream
import java.time.LocalDate

/**
 * A [StatementImporter] with canned answers: the sniff result depends only on the file name (a "known" bank file
 * matches [preset]; anything else is an unknown layout) and every parse returns [rows] unchanged, so wizard tests
 * exercise the flow rather than CSV parsing (which `core:importer` covers on its own).
 */
class FakeStatementImporter(
    var rows: List<ParsedRow> = SAMPLE_ROWS,
    var failSniff: AppError? = null,
) : StatementImporter {

    val preset = ImportPreset(
        id = "hdfc",
        name = "HDFC Bank",
        headerSignature = listOf("date", "narration", "withdrawal amt.", "deposit amt."),
        mapping = ColumnMapping(
            roles = mapOf(0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.DEBIT, 3 to ColumnRole.CREDIT),
            dateFormat = "dd/MM/yy",
        ),
    )
    var sniffs = 0
    var parses = 0

    override suspend fun sniff(source: ImportSource): Outcome<SniffResult> {
        sniffs++
        failSniff?.let { return Outcome.failure(it) }
        val known = source.name.startsWith("hdfc")
        return Outcome.success(
            SniffResult(
                encoding = "UTF-8",
                delimiter = ',',
                headerRow = 0,
                headers = if (known) listOf("Date", "Narration", "Withdrawal Amt.", "Deposit Amt.") else listOf("A", "B", "C"),
                sampleRows = listOf(listOf("01/09/26", "UPI-SWIGGY", "349.00", "")),
                preset = preset.takeIf { known },
                suggestedMapping = if (known) preset.mapping else ColumnMapping(emptyMap(), "dd/MM/yyyy"),
                suggestedDateFormats = listOf("dd/MM/yy"),
            ),
        )
    }

    override suspend fun parse(source: ImportSource, sniff: SniffResult, mapping: ColumnMapping): Outcome<List<ParsedRow>> {
        parses++
        return Outcome.success(rows)
    }

    override fun presets(): List<ImportPreset> = listOf(preset)

    /** Pretends any sample that contains a month name is `d MMM yyyy`, otherwise two-digit `dd/MM/yy`. */
    override fun dateFormatsFor(samples: List<String>): List<String> =
        if (samples.any { s -> s.any { it.isLetter() } }) listOf("d MMM yyyy") else listOf("dd/MM/yy", "dd/MM/yyyy")

    companion object {
        fun source(name: String = "hdfc-sep.csv"): ImportSource =
            ImportSource(name, 3, ByteSource { ByteArrayInputStream("abc".toByteArray()) })

        val SAMPLE_ROWS: List<ParsedRow> = listOf(
            ParsedRow(1, listOf("01/09/26", "UPI-SWIGGY-ORDER", "349.00", ""), LocalDate.of(2026, 9, 1), "UPI-SWIGGY-ORDER", -349_00),
            ParsedRow(2, listOf("02/09/26", "SALARY ACME", "", "80000.00"), LocalDate.of(2026, 9, 2), "SALARY ACME", 80_000_00),
            ParsedRow(3, listOf("03/09/26", "AMAZON.IN", "1250.50", ""), LocalDate.of(2026, 9, 3), "AMAZON.IN", -1_250_50),
            ParsedRow(4, listOf("bad", "", "", ""), error = "Unreadable date “bad”"),
        )
    }
}
