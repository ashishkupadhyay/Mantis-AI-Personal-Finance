package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationRequest
import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleBasedCategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ApplyRuleUseCaseTest {

    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val groceries = categories.categories.value.values.first { it.key == "food.groceries" }

    private fun row(
        id: String,
        description: String,
        source: CategorySource,
        category: CategoryId? = null,
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
            id = TransactionId(id), accountId = AccountId("bank"), type = type,
            amount = Money.ofMajor(if (type == TransactionType.INCOME) 10 else -10, Currency.INR),
            postedAt = Instant.EPOCH, postedLocalDate = LocalDate.of(2026, 9, 1), descriptionRaw = description,
            categoryId = category, categorySource = source, fingerprint = newId(), meta = testMeta(),
        )

    private val transactions = FakeTransactionRepository(
        listOf(
            row("model", "SWIGGY ORDER 1", CategorySource.ON_DEVICE_MODEL, groceries.id),
            row("none", "swiggy instamart", CategorySource.NONE),
            row("user", "SWIGGY ORDER 2", CategorySource.USER, groceries.id),
            row("already", "SWIGGY ORDER 3", CategorySource.MERCHANT_DB, delivery.id),
            row("other", "ZOMATO", CategorySource.NONE),
            row("transfer", "SWIGGY REFUND TRANSFER", CategorySource.USER, type = TransactionType.TRANSFER),
        ),
    )

    private val rule = CategoryRule(RuleId("r1"), RuleMatchType.CONTAINS, "swiggy", categoryId = delivery.id, meta = testMeta())

    @Test
    fun `FR_CAT_6 applying a rule relabels matches except manual labels, rows already there and transfers`() = runBlocking {
        ApplyRuleUseCase(transactions).invoke(rule) shouldBe 2

        transactions.getTransaction(TransactionId("model"))?.let {
            it.categoryId shouldBe delivery.id
            it.categorySource shouldBe CategorySource.USER_RULE
            it.categoryConfidence shouldBe 1f
        }
        transactions.getTransaction(TransactionId("none"))?.categoryId shouldBe delivery.id
        transactions.getTransaction(TransactionId("user"))?.categoryId shouldBe groceries.id
        transactions.getTransaction(TransactionId("other"))?.categoryId shouldBe null
        transactions.getTransaction(TransactionId("transfer"))?.categoryId shouldBe null
        ApplyRuleUseCase(transactions).invoke(rule) shouldBe 0
    }

    @Test
    fun `the pipeline labels new entries from rules with USER_RULE at full confidence`() = runBlocking {
        categories.saveRule(rule)
        val pipeline = RuleBasedCategorizationPipeline(categories)
        val request = CategorizationRequest(
            "Swiggy dinner", Money.ofMajor(-3, Currency.INR), TransactionType.EXPENSE, AccountId("bank"), Instant.EPOCH,
        )

        val suggestion = pipeline.categorize(request)
        suggestion?.categoryId shouldBe delivery.id
        suggestion?.source shouldBe CategorySource.USER_RULE
        suggestion?.needsReview shouldBe false
        pipeline.categorize(request.copy(descriptionRaw = "unknown")) shouldBe null
    }
}
