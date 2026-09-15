package io.github.ashishkupadhyay.mantis.core.common.money

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MoneyFormatterTest {

    private val inr = Currency.INR
    private val indian = MoneyFormatter(NumberStyle.INDIAN)
    private val intl = MoneyFormatter(NumberStyle.INTERNATIONAL)

    @Test
    fun `indian grouping uses lakh and crore`() {
        MoneyFormatter.groupDigits("1", NumberStyle.INDIAN) shouldBe "1"
        MoneyFormatter.groupDigits("123", NumberStyle.INDIAN) shouldBe "123"
        MoneyFormatter.groupDigits("1234", NumberStyle.INDIAN) shouldBe "1,234"
        MoneyFormatter.groupDigits("123456", NumberStyle.INDIAN) shouldBe "1,23,456"
        MoneyFormatter.groupDigits("1234567", NumberStyle.INDIAN) shouldBe "12,34,567"
        MoneyFormatter.groupDigits("123456789", NumberStyle.INDIAN) shouldBe "12,34,56,789"
    }

    @Test
    fun `international grouping uses thousands`() {
        MoneyFormatter.groupDigits("1234567", NumberStyle.INTERNATIONAL) shouldBe "1,234,567"
        MoneyFormatter.groupDigits("123", NumberStyle.INTERNATIONAL) shouldBe "123"
    }

    @Test
    fun `formats rupees with symbol, minor digits and unicode minus`() {
        indian.format(Money(12_345_600, inr)) shouldBe "₹1,23,456.00"
        indian.format(Money(-34_900, inr)) shouldBe "−₹349.00"
        indian.format(Money(5, inr)) shouldBe "₹0.05"
        intl.format(Money(12_345_600, inr)) shouldBe "₹123,456.00"
    }

    @Test
    fun `parts split sign, symbol, integer, fraction and code`() {
        indian.parts(Money(-12_345_600, inr)) shouldBe MoneyParts("−", "₹", "1,23,456", ".00", "")
        indian.parts(Money(1500, Currency.JPY)) shouldBe MoneyParts("", "¥", "1,500", "", "")
        MoneyFormatter(NumberStyle.INDIAN, SymbolStyle.CODE).parts(Money(1234500, inr), showMinor = false) shouldBe
            MoneyParts("", "", "12,345", "", " INR")
        indian.parts(Money(-34_900, inr)).toString() shouldBe indian.format(Money(-34_900, inr))
    }

    @Test
    fun `zero-minor-digit currencies and code style`() {
        indian.format(Money(1500, Currency.JPY)) shouldBe "¥1,500"
        MoneyFormatter(NumberStyle.INDIAN, SymbolStyle.CODE).format(Money(1234500, inr)) shouldBe "12,345.00 INR"
        MoneyFormatter(NumberStyle.INDIAN, SymbolStyle.NONE).format(Money(1234500, inr), showMinor = false) shouldBe "12,345"
    }

    @Test
    fun `hidden amounts are masked regardless of value`() {
        indian.format(Money(12_345_600, inr), hidden = true) shouldBe MoneyFormatter.HIDDEN_MASK
        indian.formatCompact(Money(12_345_600, inr), hidden = true) shouldBe MoneyFormatter.HIDDEN_MASK
    }

    @Test
    fun `explicit plus sign when requested`() {
        MoneyFormatter(alwaysShowSign = true).format(Money(100, inr)) shouldBe "+₹1.00"
        MoneyFormatter(alwaysShowSign = true).format(Money(0, inr)) shouldBe "₹0.00"
    }

    @Test
    fun `compact indian and international forms`() {
        indian.formatCompact(Money.ofMajor(1_234L, inr)) shouldBe "₹1.2K"
        indian.formatCompact(Money.ofMajor(120_000L, inr)) shouldBe "₹1.2L"
        indian.formatCompact(Money.ofMajor(34_000_000L, inr)) shouldBe "₹3.4Cr"
        indian.formatCompact(Money.ofMajor(999L, inr)) shouldBe "₹999"
        indian.formatCompact(Money.ofMajor(-250_000L, inr)) shouldBe "−₹2.5L"
        intl.formatCompact(Money.ofMajor(3_400_000L, inr)) shouldBe "₹3.4M"
        intl.formatCompact(Money.ofMajor(2_000_000_000L, inr)) shouldBe "₹2B"
    }
}
