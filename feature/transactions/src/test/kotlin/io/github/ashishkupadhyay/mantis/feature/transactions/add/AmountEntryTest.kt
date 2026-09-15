package io.github.ashishkupadhyay.mantis.feature.transactions.add

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.math.BigDecimal

class AmountEntryTest {

    private fun type(vararg keys: KeypadKey): AmountEntry = keys.fold(AmountEntry()) { entry, key -> entry.press(key) }

    private fun digits(text: String): Array<KeypadKey> = text.map { if (it == '.') KeypadKey.Dot else KeypadKey.Digit(it) }.toTypedArray()

    @Test
    fun `FR_TXN_1 digits and a decimal point build a plain amount`() {
        val entry = type(*digits("349.5"))
        entry.value shouldBe BigDecimal("349.5")
        entry.display shouldBe "349.5"
        entry.isEmpty shouldBe false
    }

    @Test
    fun `operators fold left to right and equals collapses the expression`() {
        val pending = type(*digits("349"), KeypadKey.Plus, *digits("51"))
        pending.display shouldBe "349 + 51"
        pending.value shouldBe BigDecimal("400")

        val chained = pending.press(KeypadKey.Minus).press(KeypadKey.Digit('1')).press(KeypadKey.Digit('0'))
        chained.display shouldBe "400 − 10"
        chained.value shouldBe BigDecimal("390")

        val folded = chained.press(KeypadKey.Equals)
        folded.display shouldBe "390"
        folded.operator shouldBe null
    }

    @Test
    fun `backspace edits the operand then removes the operator`() {
        val entry = type(*digits("12"), KeypadKey.Plus, *digits("3"))
        entry.press(KeypadKey.Backspace).display shouldBe "12 +"
        entry.press(KeypadKey.Backspace).press(KeypadKey.Backspace).display shouldBe "12"
        type(*digits("7")).press(KeypadKey.Backspace).press(KeypadKey.Backspace).isEmpty shouldBe true
    }

    @Test
    fun `input is bounded - two decimals, twelve digits, no leading zeros, never negative`() {
        type(*digits("1.234")).value shouldBe BigDecimal("1.23")
        type(*digits("1234567890123")).value shouldBe BigDecimal("123456789012")
        type(*digits("007")).display shouldBe "7"
        type(*digits(".5")).display shouldBe "0.5"
        type(*digits("5"), KeypadKey.Minus, *digits("9")).value shouldBe BigDecimal.ZERO
        type(KeypadKey.Plus).isEmpty shouldBe true // an operator with nothing before it is ignored
        type(*digits("42"), KeypadKey.Clear).isEmpty shouldBe true
    }
}
