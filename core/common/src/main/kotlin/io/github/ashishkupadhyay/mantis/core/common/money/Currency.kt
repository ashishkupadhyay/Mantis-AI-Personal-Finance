package io.github.ashishkupadhyay.mantis.core.common.money

/**
 * An ISO 4217 currency. Kept as our own type (not `java.util.Currency`) so the domain stays
 * platform-neutral and so unknown codes from imported statements never throw.
 *
 * @property code ISO 4217 alphabetic code, upper-case (`INR`, `USD`).
 * @property minorDigits number of minor-unit digits (2 for INR, 0 for JPY, 3 for KWD).
 * @property symbol display symbol; falls back to the code when no widely recognised symbol exists.
 */
data class Currency(val code: String, val minorDigits: Int, val symbol: String) {

    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Invalid ISO 4217 code: $code" }
        require(minorDigits in 0..4) { "minorDigits out of range: $minorDigits" }
    }

    /** Multiplier from major to minor units, e.g. 100 for INR. */
    val minorPerMajor: Long get() = POWERS_OF_TEN[minorDigits]

    companion object {
        private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1_000, 10_000)

        val INR = Currency("INR", 2, "₹")
        val USD = Currency("USD", 2, "$")
        val EUR = Currency("EUR", 2, "€")
        val GBP = Currency("GBP", 2, "£")
        val JPY = Currency("JPY", 0, "¥")
        val AED = Currency("AED", 2, "AED")
        val SGD = Currency("SGD", 2, "S$")
        val AUD = Currency("AUD", 2, "A$")
        val CAD = Currency("CAD", 2, "C$")
        val CHF = Currency("CHF", 2, "CHF")
        val KWD = Currency("KWD", 3, "KWD")

        private val known: Map<String, Currency> =
            listOf(INR, USD, EUR, GBP, JPY, AED, SGD, AUD, CAD, CHF, KWD).associateBy { it.code }

        /**
         * Resolves a code. Known currencies carry symbols and minor digits; anything else is accepted with
         * 2 minor digits and the code as its symbol, so an odd statement never crashes an import.
         */
        fun of(code: String): Currency {
            val normalized = code.trim().uppercase()
            return known[normalized] ?: Currency(normalized, minorDigits = 2, symbol = normalized)
        }
    }
}
