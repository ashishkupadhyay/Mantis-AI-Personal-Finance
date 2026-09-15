package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ModelInvariantsTest {

    private val meta = SyncMeta.local(hlc = 1L, deviceId = DeviceId("dev"))
    private val inr = Currency.INR
    private val account = AccountId("a1")

    private fun txn(amount: Long, type: TransactionType = TransactionType.EXPENSE, splits: List<Split> = emptyList()) = Transaction(
        id = TransactionId("t1"), accountId = account, type = type, amount = Money(amount, inr),
        postedAt = Instant.EPOCH, postedLocalDate = LocalDate.EPOCH, descriptionRaw = "UPI-SWIGGY",
        fingerprint = "fp", splits = splits, meta = meta,
    )

    @Test
    fun `expense amounts must be non-positive and income non-negative`() {
        txn(-100).amount.minor shouldBe -100
        shouldThrow<IllegalArgumentException> { txn(100) }
        shouldThrow<IllegalArgumentException> { txn(-100, TransactionType.INCOME) }
        txn(100, TransactionType.INCOME).countsAsSpending shouldBe false
        txn(-100).countsAsSpending shouldBe true
    }

    @Test
    fun `splits must sum to the amount and share its currency`() {
        val ok = txn(
            -1210_00,
            splits = listOf(
                Split(SplitId("s1"), CategoryId("groceries"), Money(-1030_00, inr)),
                Split(SplitId("s2"), CategoryId("care"), Money(-180_00, inr), origin = SplitOrigin.RECEIPT_ITEMS),
            ),
        )
        ok.splits shouldHaveSize 2
        shouldThrow<IllegalArgumentException> {
            txn(-1210_00, splits = listOf(Split(SplitId("s1"), CategoryId("x"), Money(-100_00, inr))))
        }
        shouldThrow<IllegalArgumentException> {
            txn(-100, splits = listOf(Split(SplitId("s1"), CategoryId("x"), Money(-100, Currency.USD))))
        }
    }

    @Test
    fun `category source precedence matches the sync merge rules`() {
        CategorySource.USER.outranks(CategorySource.ON_DEVICE_MODEL) shouldBe true
        CategorySource.USER_RULE.outranks(CategorySource.USER) shouldBe false
        CategorySource.MERCHANT_DB.outranks(CategorySource.LLM) shouldBe true
        CategorySource.LLM.outranks(CategorySource.ON_DEVICE_MODEL) shouldBe false
        CategorySource.ON_DEVICE_MODEL.outranks(CategorySource.IMPORT) shouldBe true
    }

    @Test
    fun `account validation`() {
        val acc = Account(
            id = account, name = "HDFC", type = AccountType.CREDIT_CARD, currency = inr,
            openingBalance = Money.zero(inr), openingDate = LocalDate.EPOCH, last4 = "1234", statementDay = 5, dueDay = 25, meta = meta,
        )
        acc.isLiability shouldBe true
        shouldThrow<IllegalArgumentException> { acc.copy(last4 = "12a4") }
        shouldThrow<IllegalArgumentException> { acc.copy(openingBalance = Money.zero(Currency.USD)) }
        shouldThrow<IllegalArgumentException> { acc.copy(dueDay = 32) }
    }

    @Test
    fun `budget validation and defaults`() {
        val b = Budget(id = BudgetId("b1"), name = "Food", amount = Money.ofMajor(8000L, inr), meta = meta)
        b.thresholds shouldBe listOf(50, 80, 100)
        b.coversAllSpending shouldBe true
        shouldThrow<IllegalArgumentException> { b.copy(amount = Money.zero(inr)) }
        shouldThrow<IllegalArgumentException> { b.copy(thresholds = listOf(80, 50)) }
        shouldThrow<IllegalArgumentException> { b.copy(period = BudgetPeriodType.CUSTOM) }
    }

    @Test
    fun `tags normalise for uniqueness`() {
        Tag.normalize("#Goa Trip") shouldBe "goa-trip"
        Tag.normalize("goa_trip") shouldBe "goa-trip"
        Tag(TagId("t"), "#Goa Trip", meta = meta).normalizedName shouldBe "goa-trip"
    }

    @Test
    fun `default taxonomy has ten groups, unique keys and the classifier label space`() {
        DefaultTaxonomy.groups shouldHaveSize 11 // 10 groups + System
        DefaultTaxonomy.leafKeys shouldHaveSize DefaultTaxonomy.leaves.size
        DefaultTaxonomy.leaves.all { it.key.startsWith(DefaultTaxonomy.groupKeyOf(it.key) + ".") } shouldBe true
        DefaultTaxonomy.classifierLabels.contains(DefaultTaxonomy.KEY_EXCLUDED) shouldBe false
        DefaultTaxonomy.classifierLabels.contains(DefaultTaxonomy.KEY_TRANSFER) shouldBe true
        DefaultTaxonomy.classifierLabels.contains("food.delivery") shouldBe true
    }
}
