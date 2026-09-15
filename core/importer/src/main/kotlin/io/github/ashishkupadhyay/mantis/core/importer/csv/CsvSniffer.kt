package io.github.ashishkupadhyay.mantis.core.importer.csv

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/** Encoding, delimiter and header-row detection from the first 64 KB of a file (doc 02 §7 step 1, FR-IMP-2). */
object CsvSniffer {

    /** [text] is the decoded sample without its BOM, so callers never see U+FEFF in a cell. */
    data class Sniff(val charset: Charset, val delimiter: Char, val headerRow: Int, val headerCells: List<String>, val text: String)

    private val DELIMITERS = listOf(',', ';', '\t', '|')
    private const val SAMPLE_BYTES = 64 * 1024
    private const val SAMPLE_LINES = 60
    private const val CONSISTENCY_WEIGHT = 0.7
    private const val TIE_BREAK = 1000.0
    private const val MIN_ROLE_HITS = 2
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** Keywords a header cell may contain, per role family; two hits on one line make it the header. */
    val HEADER_KEYWORDS: List<String> = listOf(
        "date", "narration", "description", "particulars", "details", "remarks", "transaction",
        "debit", "withdrawal", "credit", "deposit", "amount", "balance", "reference", "chq", "ref",
    )

    fun sniff(bytes: ByteArray): Sniff {
        val (charset, offset) = detectCharset(bytes)
        val text = String(bytes, offset, bytes.size - offset, charset)
        val lines = text.lineSequence().take(SAMPLE_LINES).toList()
        val delimiter = detectDelimiter(lines)
        val headerRow = detectHeaderRow(lines, delimiter)
        val headerCells = lines.getOrNull(headerRow)?.let { splitLine(it, delimiter) }.orEmpty()
        return Sniff(charset, delimiter, headerRow, headerCells, text)
    }

    fun readSample(stream: InputStream): ByteArray = stream.use { input ->
        val buffer = ByteArray(SAMPLE_BYTES)
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        buffer.copyOf(total)
    }

    /** BOM first, then strict UTF-8 validation, then Windows-1252 — the three encodings Indian bank exports use. */
    fun detectCharset(bytes: ByteArray): Pair<Charset, Int> = when {
        bytes.size >= UTF8_BOM.size && bytes.copyOf(UTF8_BOM.size).contentEquals(UTF8_BOM) -> Charsets.UTF_8 to UTF8_BOM.size
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        isValid(Charsets.UTF_8.newDecoder(), bytes) -> Charsets.UTF_8 to 0
        else -> Charset.forName("windows-1252") to 0
    }

    private fun isValid(decoder: CharsetDecoder, bytes: ByteArray): Boolean {
        decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)); true }.getOrDefault(false)
    }

    /**
     * The delimiter that splits lines most consistently: agreement on the per-line count among the lines that contain
     * it, weighted by how many lines do (preamble lines contain none), with more columns as the tie-breaker.
     */
    fun detectDelimiter(lines: List<String>): Char {
        val candidates = lines.filter { it.isNotBlank() }
        if (candidates.isEmpty()) return ','
        return DELIMITERS.maxByOrNull { delimiter ->
            val counts = candidates.map { line -> countOutsideQuotes(line, delimiter) }.filter { it > 0 }
            if (counts.isEmpty()) return@maxByOrNull 0.0
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }!!
            val consistency = mode.value.toDouble() / counts.size
            val coverage = counts.size.toDouble() / candidates.size
            consistency * CONSISTENCY_WEIGHT + coverage * (1 - CONSISTENCY_WEIGHT) + mode.key / TIE_BREAK
        } ?: ','
    }

    /** First line with at least two header keywords; falls back to the first non-blank line. */
    fun detectHeaderRow(lines: List<String>, delimiter: Char): Int {
        val index = lines.indexOfFirst { line ->
            val cells = splitLine(line, delimiter).map { it.lowercase() }
            cells.count { cell -> HEADER_KEYWORDS.any { it in cell } } >= MIN_ROLE_HITS
        }
        return if (index >= 0) index else lines.indexOfFirst { it.isNotBlank() }.coerceAtLeast(0)
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var inQuotes = false
        var count = 0
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        CsvReader(delimiter).read(line.reader()) { _, row -> cells += row }
        return cells.map { it.trim() }
    }
}
