package io.github.ashishkupadhyay.mantis.core.common.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    private val inr = Currency.INR
    private val safeMinor = Arb.long(-1_000_000_000_000L, 1_000_000_000_000L) // ±₹10 billion, no overflow in sums

    @Test
    fun `addition is commutative and associative, zero is neutral`() = runTest {
        checkAll(safeMinor, safeMinor, safeMinor) { a, b, c ->
            val (ma, mb, mc) = listOf(a, b, c).map { Money(it, inr) }
            (ma + mb) shouldBe (mb + ma)
            ((ma + mb) + mc) shouldBe (ma + (mb + mc))
            (ma + Money.zero(inr)) shouldBe ma
            (ma - ma) shouldBe Money.zero(inr)
        }
    }

    @Test
    fun `split parts always sum to the whole and differ by at most one minor unit`() = runTest {
        checkAll(safeMinor, Arb.int(1, 12)) { minor, parts ->
            val money = Money(minor, inr)
            val split = money.split(parts)
            split.size shouldBe parts
            Money.sum(split, inr) shouldBe money
            val sizes = split.map { it.minor }
            (sizes.max() - sizes.min() <= parts) shouldBe true
        }
    }

    @Test
    fun `major conversion round-trips through ofMajor`() = runTest {
        checkAll(safeMinor) { minor ->
            val money = Money(minor, inr)
            Money.ofMajor(money.major, inr) shouldBe money
        }
    }

    @Test
    fun `ofMajor rounds half-even to the currency's minor digits`() {
        Money.ofMajor("123.455", inr) shouldBe Money(12346, inr)
        Money.ofMajor("123.445", inr) shouldBe Money(12344, inr)
        Money.ofMajor("1", Currency.JPY) shouldBe Money(1, Currency.JPY)
        Money.ofMajor("1.2345", Currency.KWD) shouldBe Money(1234, Currency.KWD)
    }

    @Test
    fun `times with a decimal factor rounds half-even`() {
        Money(1000, inr).times(BigDecimal("0.125")) shouldBe Money(125, inr)
        Money(1001, inr).times(BigDecimal("0.5")) shouldBe Money(500, inr) // 500.5 → 500 (even)
        Money(1003, inr).times(BigDecimal("0.5")) shouldBe Money(502, inr) // 501.5 → 502 (even)
    }

    @Test
    fun `mixing currencies throws`() {
        shouldThrow<IllegalArgumentException> { Money(1, inr) + Money(1, Currency.USD) }
        shouldThrow<IllegalArgumentException> { Money(1, inr).compareTo(Money(1, Currency.USD)) }
    }

    @Test
    fun `sign helpers and abs`() {
        Money(-5, inr).isNegative shouldBe true
        Money(-5, inr).abs() shouldBe Money(5, inr)
        Money(0, inr).isZero shouldBe true
        Money(7, inr).isPositive shouldBe true
    }

    @Test
    fun `ratioTo handles zero denominators`() {
        Money(50, inr).ratioTo(Money(200, inr)) shouldBe 0.25
        Money(50, inr).ratioTo(Money.zero(inr)) shouldBe 0.0
    }

    @Test
    fun `unknown currency codes are accepted with two minor digits`() {
        val c = Currency.of("xyz")
        c.code shouldBe "XYZ"
        c.minorDigits shouldBe 2
        c.symbol shouldBe "XYZ"
        shouldThrow<IllegalArgumentException> { Currency.of("in") }
    }
}
