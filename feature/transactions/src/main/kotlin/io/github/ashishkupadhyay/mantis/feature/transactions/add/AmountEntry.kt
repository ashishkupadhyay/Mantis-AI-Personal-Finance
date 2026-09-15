package io.github.ashishkupadhyay.mantis.feature.transactions.add

import java.math.BigDecimal

/** One key of the calculator keypad (doc 05 §4.4). */
sealed interface KeypadKey {
    data class Digit(val value: Char) : KeypadKey
    data object Dot : KeypadKey
    data object Backspace : KeypadKey
    data object Clear : KeypadKey
    data object Plus : KeypadKey
    data object Minus : KeypadKey
    data object Equals : KeypadKey
}

/**
 * The amount being typed: a running total, an optional pending operator and the operand under the cursor.
 * `349 + 51 =` folds left to right; a new operator folds the pending one first, so `10 + 5 - 3` reads naturally.
 * Values never go below zero — the sign of a transaction comes from its type, not from the keypad.
 */
data class AmountEntry(
    val operand: String = "",
    val accumulated: BigDecimal? = null,
    val operator: KeypadKey? = null,
) {
    /** What the big number shows: the folded result of everything typed so far. */
    val value: BigDecimal
        get() = fold()

    val isEmpty: Boolean get() = operand.isEmpty() && accumulated == null

    /** "349 + 51" while an operator is pending, otherwise the number as typed. */
    val display: String
        get() = when {
            operator == null -> operand.ifEmpty { accumulated?.toPlainString() ?: "0" }
            else -> "${accumulated?.toPlainString() ?: "0"} ${symbol(operator)} $operand".trimEnd()
        }

    fun press(key: KeypadKey): AmountEntry = when (key) {
        is KeypadKey.Digit -> typeDigit(key.value)
        KeypadKey.Dot -> if ('.' in operand) this else copy(operand = operand.ifEmpty { "0" } + ".")
        KeypadKey.Backspace -> when {
            operand.isNotEmpty() -> copy(operand = operand.dropLast(1))
            operator != null -> copy(operator = null, operand = accumulated?.toPlainString().orEmpty(), accumulated = null)
            else -> this
        }
        KeypadKey.Clear -> AmountEntry()
        KeypadKey.Plus, KeypadKey.Minus -> if (isEmpty) this else AmountEntry(accumulated = fold(), operator = key)
        KeypadKey.Equals -> if (operator == null) this else AmountEntry(operand = fold().stripTrailingZeros().toPlainString())
    }

    private fun typeDigit(digit: Char): AmountEntry {
        if (operand.length >= MAX_OPERAND) return this
        if (operand == "0") return copy(operand = digit.toString())
        val fraction = operand.substringAfter('.', missingDelimiterValue = "")
        if ('.' in operand && fraction.length >= MAX_FRACTION) return this
        return copy(operand = operand + digit)
    }

    private fun fold(): BigDecimal {
        val current = operand.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val base = accumulated ?: return current
        return when (operator) {
            KeypadKey.Plus -> base + current
            KeypadKey.Minus -> (base - current).max(BigDecimal.ZERO)
            else -> base
        }
    }

    private fun symbol(key: KeypadKey?): String = when (key) {
        KeypadKey.Plus -> "+"
        KeypadKey.Minus -> "−"
        else -> ""
    }

    private companion object {
        const val MAX_OPERAND = 12
        const val MAX_FRACTION = 2
    }
}
