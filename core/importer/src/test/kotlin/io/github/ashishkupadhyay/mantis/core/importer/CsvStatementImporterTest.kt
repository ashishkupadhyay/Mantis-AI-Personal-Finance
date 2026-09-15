package io.github.ashishkupadhyay.mantis.core.importer

import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.domain.imports.ByteSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.SniffResult
import io.github.ashishkupadhyay.mantis.core.importer.csv.CsvReader
import io.github.ashishkupadhyay.mantis.core.importer.parse.AmountParser
import io.github.ashishkupadhyay.mantis.core.importer.parse.DateParser
import io.github.ashishkupadhyay.mantis.core.importer.preset.BundledPresets
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.time.LocalDate

class CsvStatementImporterTest {

    private val importer = CsvStatementImporter()

    private fun source(name: String, bytes: ByteArray) = ImportSource(name, bytes.size.toLong(), ByteSource { ByteArrayInputStream(bytes) })

    private fun resource(name: String): ImportSource {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/statements/$name")) { "missing test resource $name" }.readBytes()
        return source(name, bytes)
    }

    private fun sniff(source: ImportSource): SniffResult = runBlocking { importer.sniff(source).getOrThrow() }

    private fun parse(source: ImportSource, sniff: SniffResult = sniff(source), mapping: ColumnMapping = sniff.suggestedMapping) =
        runBlocking { importer.parse(source, sniff, mapping).getOrThrow() }

    @ParameterizedTest(name = "FR_IMP_3 preset {0} is detected and parses its golden file")
    @ValueSource(strings = ["hdfc", "icici", "sbi", "axis", "kotak", "yes", "idfc", "paytm"])
    fun goldenFiles(preset: String) {
        val source = resource("$preset.csv")
        val sniff = sniff(source)
        sniff.preset?.id shouldBe preset
        sniff.suggestedMapping.isComplete shouldBe true

        val rows = parse(source, sniff)
        rows shouldHaveSize 3
        rows.all { it.isValid } shouldBe true
        // Every golden file describes the same three movements so one assertion covers all layouts.
        rows.map { it.amountMinor } shouldContainExactly listOf(-349_00L, 80_000_00L, -1_250_50L)
        rows.map { it.date } shouldContainExactly listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3))
        rows[0].description shouldBe "UPI-SWIGGY-ORDER 1234"
    }

    @Test
    fun `FR_IMP_2 preamble rows, semicolons, UTF-16 and Windows-1252 are all sniffed correctly`() {
        val preamble = resource("preamble-semicolon.csv")
        val sniff = sniff(preamble)
        sniff.delimiter shouldBe ';'
        sniff.headerRow shouldBe 3
        sniff.headers shouldContainExactly listOf("Date", "Description", "Debit", "Credit", "Balance")
        parse(preamble, sniff).count { it.isValid } shouldBe 2

        val utf16 = source("utf16.csv", "﻿Date,Description,Amount\n01/09/2026,Café ₹,-100\n".toByteArray(Charsets.UTF_16))
        val s16 = sniff(utf16)
        s16.encoding shouldBe "UTF-16BE"
        parse(utf16, s16).single().description shouldBe "Café ₹"

        val latin = source("latin.csv", "Date,Description,Amount\n01/09/2026,Café bar,-100\n".toByteArray(Charsets.ISO_8859_1))
        sniff(latin).encoding shouldBe "windows-1252"
        parse(latin).single().description shouldBe "Café bar"
    }

    @Test
    fun `FR_IMP_4 an unknown layout gets an inferred mapping and date format the user can override`() {
        val source = source(
            "custom.csv",
            "Posted On,Details,Money Out,Money In\n2026-09-05,UBER TRIP,212.00,\n2026-09-06,REFUND,,50.00\n".toByteArray(),
        )
        val sniff = sniff(source)
        sniff.preset shouldBe null
        sniff.suggestedMapping.roles shouldBe
            mapOf(0 to ColumnRole.DATE, 1 to ColumnRole.DESCRIPTION, 2 to ColumnRole.DEBIT, 3 to ColumnRole.CREDIT)
        sniff.suggestedDateFormats.first() shouldBe "yyyy-MM-dd"

        val rows = parse(source, sniff)
        rows.map { it.amountMinor } shouldContainExactly listOf(-212_00L, 50_00L)

        val wrongFormat = parse(source, sniff, sniff.suggestedMapping.copy(dateFormat = "dd/MM/yyyy"))
        wrongFormat.all { it.error?.startsWith("Unreadable date") == true } shouldBe true
        runBlocking { importer.parse(source, sniff, ColumnMapping(emptyMap(), "dd/MM/yyyy")) }
            .errorOrNull().shouldBeInstanceOf<AppError.Validation>()
    }

    @Test
    fun `FR_IMP_5 broken rows are reported, not dropped, and a trailing unbalanced quote does not lose the file`() {
        val source = source(
            "messy.csv",
            listOf(
                "Date,Description,Debit,Credit",
                "01/09/2026,\"Quoted, with comma\",100,",
                "not a date,BAD DATE,10,",
                "02/09/2026,NO AMOUNT,,",
                "",
                "Total,,110,",
                "03/09/2026,\"Unbalanced quote,5,",
            ).joinToString("\r\n").toByteArray(),
        )
        val rows = parse(source)
        rows.map { it.error } shouldContainExactly listOf(
            null, "Unreadable date “not a date”", "Missing amount", "Unreadable date “Total”", "Missing amount",
        )
        rows[0].description shouldBe "Quoted, with comma"
        rows[0].amountMinor shouldBe -100_00L
        rows.last().description shouldBe "Unbalanced quote,5,"
    }

    @Test
    fun `NFR_20f oversized and empty files are refused before any parsing`() {
        val big = ImportSource("big.csv", 51L * 1024 * 1024, ByteSource { ByteArrayInputStream(ByteArray(0)) })
        runBlocking { importer.sniff(big) }.errorOrNull().shouldBeInstanceOf<AppError.Validation>().field shouldBe "file"
        runBlocking { importer.sniff(source("empty.csv", ByteArray(0))) }.errorOrNull().shouldBeInstanceOf<AppError.Validation>()
    }

    @Test
    fun `amounts and dates the way Indian banks print them`() {
        AmountParser.parseMinor("₹1,23,456.78") shouldBe 123_456_78L
        AmountParser.parseMinor("1,234.50 Cr") shouldBe 1_234_50L
        AmountParser.parseMinor("500 DR") shouldBe -500_00L
        AmountParser.parseMinor("(75.25)") shouldBe -75_25L
        AmountParser.parseMinor("Rs. 12") shouldBe 12_00L
        AmountParser.parseMinor("-0.005") shouldBe -1L
        AmountParser.parseMinor(" ") shouldBe null
        AmountParser.parseMinor("n/a") shouldBe null

        DateParser.infer(listOf("01/09/2026", "15/09/2026")).first() shouldBe "dd/MM/yyyy"
        DateParser.infer(listOf("13/09/2026")) shouldBe listOf("dd/MM/yyyy", "d/M/yyyy")
        DateParser.infer(listOf("2 Sep 2026", "13 Sep 2026")).first() shouldBe "d MMM yyyy"
        DateParser.parse("12/09/2026 14:05:00", "dd/MM/yyyy") shouldBe LocalDate.of(2026, 9, 12)
        DateParser.infer(listOf("nonsense")).shouldHaveSize(0)
    }

    @Test
    fun `the reader keeps quoted newlines together and tolerates bare CR line ends`() {
        val records = mutableListOf<Pair<Int, List<String>>>()
        CsvReader(',').read("a,\"multi\nline\",c\rd,\"say \"\"hi\"\"\",f\r\n".reader()) { line, cells -> records += line to cells }
        records shouldContainExactly listOf(
            0 to listOf("a", "multi\nline", "c"),
            2 to listOf("d", "say \"hi\"", "f"),
        )
        BundledPresets.all.map { it.id }.toSet().size shouldBe BundledPresets.all.size
        BundledPresets.all.all { it.mapping.isComplete } shouldBe true
        DateParser.CANDIDATES.toSet().shouldHaveSize(DateParser.CANDIDATES.size)
        runBlocking { importer.presets() }.shouldNotBeNull()
    }

    @Test
    fun `NFR_4 the parsing half of a 50k-row statement stays well inside the budget`() {
        fun statement(rows: Int): ImportSource {
            val text = buildString {
                append("Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance\n")
                repeat(rows) { i ->
                    val day = 1 + i % 28
                    val month = 1 + (i / 28) % 12
                    val debit = if (i % 3 == 0) "" else "%d.%02d".format(100 + i % 5000, i % 100)
                    val credit = if (i % 3 == 0) "80000.00" else ""
                    val date = "%02d/%02d/26".format(day, month)
                    append("$date,\"UPI-MERCHANT $i, ORDER $i\",REF$i,$date,$debit,$credit,1000.00\n")
                }
            }
            return source("hdfc-big.csv", text.toByteArray())
        }
        // A warm-up parse so the measurement is not the JIT; 5 k rows must parse in far less than the 3 s end-to-end budget.
        parse(statement(5_000))
        val small = System.nanoTime()
        parse(statement(5_000)) shouldHaveSize 5_000
        (System.nanoTime() - small) / 1_000_000 shouldBeLessThan 3_000

        val start = System.nanoTime()
        val rows = parse(statement(50_000))
        (System.nanoTime() - start) / 1_000_000 shouldBeLessThan 15_000
        rows shouldHaveSize 50_000
        rows.all { it.isValid } shouldBe true
        rows[0].amountMinor shouldBe 80_000_00L
        rows[1].amountMinor shouldBe -101_01L
        rows[1].description shouldBe "UPI-MERCHANT 1, ORDER 1"
        rows[1].reference shouldBe "REF1"
    }
}
