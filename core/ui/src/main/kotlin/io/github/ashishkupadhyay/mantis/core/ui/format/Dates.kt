package io.github.ashishkupadhyay.mantis.core.ui.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Short, human date labels used in lists and headers. Locale-formatted, year shown only when it differs. Formatters
 * are built per locale (and cached) so a locale change while the app is running is picked up on the next label.
 */
object Dates {
    private val dayMonth = LocalizedFormatter("d MMM")
    private val dayMonthYear = LocalizedFormatter("d MMM yyyy")
    private val monthYear = LocalizedFormatter("MMMM yyyy")
    private val weekdayDayMonth = LocalizedFormatter("EEE, d MMM")

    fun short(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) date.format(dayMonth) else date.format(dayMonthYear)

    /** "Today", "Yesterday", then "Sat, 12 Sep" — for day headers. */
    fun relative(date: LocalDate, today: LocalDate, todayLabel: String, yesterdayLabel: String): String = when (date) {
        today -> todayLabel
        today.minusDays(1) -> yesterdayLabel
        else -> if (date.year == today.year) date.format(weekdayDayMonth) else date.format(dayMonthYear)
    }

    fun month(date: LocalDate): String = date.format(monthYear)

    private fun LocalDate.format(formatter: LocalizedFormatter): String = format(formatter.forCurrentLocale())
}

/** One pattern, one cached [DateTimeFormatter] per locale seen. */
private class LocalizedFormatter(private val pattern: String) {
    @Volatile private var cached: Pair<Locale, DateTimeFormatter>? = null

    fun forCurrentLocale(): DateTimeFormatter {
        val locale = Locale.getDefault()
        cached?.let { (l, f) -> if (l == locale) return f }
        return DateTimeFormatter.ofPattern(pattern, locale).also { cached = locale to it }
    }
}
