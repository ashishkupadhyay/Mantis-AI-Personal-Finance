package io.github.ashishkupadhyay.mantis.core.common.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An exact amount of money in **minor units** (paise, cents) — never floating point (ADR-5).
 *
 * Sign convention follows the ledger: negative = money out, positive = money in. Arithmetic between
 * different currencies is a programming error and throws; conversions happen explicitly via the
 * rate table (FR-ACC-6), never implicitly.
 */
data class Money(val minor: Long, val currency: Currency) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L
    val isPositive: Boolean get() = minor > 0L

    /** Major-unit value as an exact decimal (e.g. 12345 paise → 123.45). */
    val major: BigDecimal
        get() = BigDecimal.valueOf(minor, currency.minorDigits)

    operator fun plus(other: Money): Money = Money(minor + requireSame(other).minor, currency)
    operator fun minus(other: Money): Money = Money(minor - requireSame(other).minor, currency)
    operator fun unaryMinus(): Money = Money(-minor, currency)
    operator fun times(factor: Long): Money = Money(minor * factor, currency)
    operator fun times(factor: Int): Money = times(factor.toLong())

    /** Multiplies by a decimal factor (e.g. an FX rate or a percentage) with half-even rounding. */
    fun times(factor: BigDecimal): Money =
        Money(BigDecimal.valueOf(minor).multiply(factor).setScale(0, RoundingMode.HALF_EVEN).longValueExact(), currency)

    /** Integer division that keeps the remainder in the last share so the parts always sum to the whole. */
    fun split(parts: Int): List<Money> {
        require(parts > 0) { "parts must be > 0" }
        val base = minor / parts
        val remainder = minor - base * parts
        return List(parts) { index -> Money(if (index == parts - 1) base + remainder else base, currency) }
    }

    fun abs(): Money = if (minor < 0) -this else this

    /** Proportion of this amount against [whole], as a fraction (0.5 = 50 %); 0 when [whole] is zero. */
    fun ratioTo(whole: Money): Double {
        requireSame(whole)
        return if (whole.minor == 0L) 0.0 else minor.toDouble() / whole.minor.toDouble()
    }

    override fun compareTo(other: Money): Int = minor.compareTo(requireSame(other).minor)

    override fun toString(): String = "${currency.code} ${major.toPlainString()}"

    private fun requireSame(other: Money): Money {
        require(other.currency.code == currency.code) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}"
        }
        return other
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        /** Builds from a major-unit decimal such as `"123.45"`, rounding half-even to the currency's minor digits. */
        fun ofMajor(major: BigDecimal, currency: Currency): Money =
            Money(major.setScale(currency.minorDigits, RoundingMode.HALF_EVEN).unscaledValue().longValueExact(), currency)

        fun ofMajor(major: String, currency: Currency): Money = ofMajor(BigDecimal(major), currency)

        fun ofMajor(major: Long, currency: Currency): Money = Money(major * currency.minorPerMajor, currency)

        /** Sums amounts of one currency; an empty list yields zero in [currency]. */
        fun sum(amounts: Iterable<Money>, currency: Currency): Money =
            amounts.fold(zero(currency)) { acc, m -> acc + m }
    }
}

fun Iterable<Money>.sumOf(currency: Currency): Money = Money.sum(this, currency)
