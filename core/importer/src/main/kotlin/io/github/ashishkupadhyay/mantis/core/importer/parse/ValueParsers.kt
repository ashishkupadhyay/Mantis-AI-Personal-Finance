package io.github.ashishkupadhyay.mantis.core.importer.parse

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Amounts the way Indian statements print them: `₹1,23,456.78`, `1,234.50 Cr`, `(500.00)`, `-500`, `500 DR`,
 * `Rs. 12.00`. Returns minor units with the sign the text implies (`Dr`/parentheses/minus = negative).
 */
object AmountParser {
    private val NOISE = Regex("[₹$€£]|rs\\.?|inr", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("\\bcr\\b", RegexOption.IGNORE_CASE)
    private val DEBIT = Regex("\\bdr\\b", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("-?\\d+(\\.\\d+)?")

    /** `null` when the text holds no number at all (an empty Debit cell on a credit row is normal). */
    fun parseMinor(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        var negative = trimmed.startsWith("(") && trimmed.endsWith(")") || DEBIT.containsMatchIn(trimmed)
        if (CREDIT.containsMatchIn(trimmed)) negative = false
        val cleaned = NOISE.replace(trimmed, "").replace(",", "").replace(" ", "").trim('(', ')', ' ')
        val number = NUMBER.find(cleaned)?.value ?: return null
        val value = number.toBigDecimalOrNull() ?: return null
        val minor = value.abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
        val sign = if (negative || number.startsWith("-")) -1 else 1
        return sign * minor
    }

    fun format(minor: Long): String = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}

/** Date patterns bank exports use, tried in order; the first that parses every sample row wins (FR-IMP-4). */
object DateParser {
    val CANDIDATES: List<String> = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yy", "dd-MM-yy", "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy",
        "d-MMM-yyyy", "dd-MMM-yy", "dd MMM yy", "d MMM yy", "MM/dd/yyyy", "yyyy/MM/dd", "d/M/yyyy", "d-M-yyyy",
        "dd.MM.yyyy", "MMM dd, yyyy", "yyyyMMdd",
    )

    private val formatters: Map<String, DateTimeFormatter> =
        CANDIDATES.associateWith { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /** Patterns that parse *all* non-blank [samples], most specific first. */
    fun infer(samples: List<String>): List<String> {
        val values = samples.map { normalise(it) }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return emptyList()
        return CANDIDATES.filter { pattern -> values.all { parse(it, pattern) != null } }
    }

    fun parse(text: String, pattern: String): LocalDate? {
        val formatter = formatters[pattern]
            ?: runCatching { DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH) }.getOrNull()
            ?: return null
        return runCatching { LocalDate.parse(normalise(text), formatter) }.getOrNull()
    }

    /** Drops a time part (`12/09/2026 14:05:00`) and squeezes whitespace so one pattern covers both shapes. */
    private fun normalise(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").replace(Regex("[ T]\\d{1,2}:\\d{2}(:\\d{2})?.*$"), "")
}
