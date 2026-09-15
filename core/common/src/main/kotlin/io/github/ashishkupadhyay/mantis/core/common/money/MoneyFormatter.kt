package io.github.ashishkupadhyay.mantis.core.common.money

/** Digit grouping style (FR-SET-2). */
enum class NumberStyle {
    /** `12,34,567.89` — groups of 3 then 2 (lakh / crore). */
    INDIAN,

    /** `1,234,567.89` — groups of 3. */
    INTERNATIONAL,
}

/** How the currency is shown. */
enum class SymbolStyle { SYMBOL, CODE, NONE }

/**
 * A formatted amount broken into the pieces UIs style differently (doc 05 §2.3: symbol smaller, minor units
 * lighter). `toString()` is the plain rendering, identical to [MoneyFormatter.format].
 */
data class MoneyParts(
    /** `−` (U+2212), `+` or empty. */
    val sign: String,
    /** Currency symbol when [SymbolStyle.SYMBOL], otherwise empty. */
    val symbol: String,
    /** Grouped integer digits, e.g. `1,23,456`. */
    val integer: String,
    /** Decimal separator plus minor digits (`.00`), or empty when minor units are not shown. */
    val fraction: String,
    /** ` INR` when [SymbolStyle.CODE], otherwise empty. */
    val code: String,
) {
    override fun toString(): String = "$sign$symbol$integer$fraction$code"
}

/**
 * Pure-Kotlin money formatting so the same output is produced on device, in unit tests and on the
 * backend (which mirrors it in Python). Locale-specific separators are intentionally fixed to `,` and
 * `.` — Indian and international English conventions — and honoured by every screen (doc 05 §2.3).
 *
 * `hidden = true` renders the "hide amounts" mask (FR-PRV-6).
 */
class MoneyFormatter(
    private val style: NumberStyle = NumberStyle.INDIAN,
    private val symbolStyle: SymbolStyle = SymbolStyle.SYMBOL,
    private val alwaysShowSign: Boolean = false,
) {

    fun format(money: Money, hidden: Boolean = false, showMinor: Boolean = true): String =
        if (hidden) HIDDEN_MASK else parts(money, showMinor).toString()

    fun parts(money: Money, showMinor: Boolean = true): MoneyParts {
        val currency = money.currency
        val negative = money.minor < 0
        val absMinor = if (money.minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(money.minor)
        val majorPart = absMinor / currency.minorPerMajor
        val minorPart = absMinor % currency.minorPerMajor

        val fraction = if (showMinor && currency.minorDigits > 0) {
            "." + minorPart.toString().padStart(currency.minorDigits, '0')
        } else {
            ""
        }
        val sign = when {
            negative -> "−" // U+2212 minus sign, tabular-friendly
            alwaysShowSign && money.minor > 0 -> "+"
            else -> ""
        }
        return MoneyParts(
            sign = sign,
            symbol = if (symbolStyle == SymbolStyle.SYMBOL) currency.symbol else "",
            integer = groupDigits(majorPart.toString(), style),
            fraction = fraction,
            code = if (symbolStyle == SymbolStyle.CODE) " ${currency.code}" else "",
        )
    }

    /** Compact form for tight spaces: ₹1.2L, ₹3.4Cr (Indian) or ₹1.2K, ₹3.4M (international). */
    fun formatCompact(money: Money, hidden: Boolean = false): String {
        if (hidden) return HIDDEN_MASK
        val currency = money.currency
        val negative = money.minor < 0
        val absMajor = kotlin.math.abs(money.minor).toDouble() / currency.minorPerMajor
        val (value, suffix) = when (style) {
            NumberStyle.INDIAN -> when {
                absMajor >= CRORE -> absMajor / CRORE to "Cr"
                absMajor >= LAKH -> absMajor / LAKH to "L"
                absMajor >= THOUSAND -> absMajor / THOUSAND to "K"
                else -> absMajor to ""
            }
            NumberStyle.INTERNATIONAL -> when {
                absMajor >= BILLION -> absMajor / BILLION to "B"
                absMajor >= MILLION -> absMajor / MILLION to "M"
                absMajor >= THOUSAND -> absMajor / THOUSAND to "K"
                else -> absMajor to ""
            }
        }
        val number = if (suffix.isEmpty()) {
            groupDigits(value.toLong().toString(), style)
        } else {
            val rounded = kotlin.math.round(value * TENTHS) / TENTHS
            if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        }
        val sign = if (negative) "−" else ""
        val prefix = if (symbolStyle == SymbolStyle.SYMBOL) currency.symbol else ""
        val suffixCode = if (symbolStyle == SymbolStyle.CODE) " ${currency.code}" else ""
        return "$sign$prefix$number$suffix$suffixCode"
    }

    companion object {
        const val HIDDEN_MASK = "••••"
        private const val THOUSAND = 1_000.0
        private const val LAKH = 100_000.0
        private const val CRORE = 10_000_000.0
        private const val MILLION = 1_000_000.0
        private const val BILLION = 1_000_000_000.0
        private const val TENTHS = 10.0

        /** Groups an unsigned integer string: `1234567` → `12,34,567` (Indian) or `1,234,567`. */
        fun groupDigits(integer: String, style: NumberStyle): String {
            if (integer.length <= 3) return integer
            return when (style) {
                NumberStyle.INTERNATIONAL -> integer.reversed().chunked(3).joinToString(",").reversed()
                NumberStyle.INDIAN -> {
                    val last3 = integer.takeLast(3)
                    val rest = integer.dropLast(3)
                    rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
                }
            }
        }
    }
}
