package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.log.NoOpLogger
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.categorization.NoCategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeSpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTagRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObserver
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.CategorySuggestion
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AddTransactionUseCaseTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(Instant.parse("2026-09-13T04:30:00Z"), zone)
    private val transactions = FakeTransactionRepository()
    private val categories = FakeCategoryRepository.withDefaults()
    private val tags = FakeTagRepository()
    private val written = mutableListOf<List<Transaction>>()
    private val observer = TransactionWriteObserver { written += it }
    private val bank = AccountId("bank")
    private val card = AccountId("card")

    private fun useCase(pipeline: CategorizationPipeline = NoCategorizationPipeline) = AddTransactionUseCase(
        transactions, categories, tags, pipeline,
        TransactionWriteObservers(setOf(observer), NoOpLogger), clock, UuidV7(nowMillis = clock::epochMillis),
    )

    private fun draft(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = 34_900L,
        description: String = "Swiggy",
    ) = TransactionDraft(type, Money(amount, Currency.INR), bank, clock.now(), description)

    private suspend fun groceries() = categories.byKey("food.groceries").shouldNotBeNull()

    @Test
    fun `FR_TXN_1 an expense is stored negative in the user's calendar day with the chosen category as USER`() = runBlocking {
        val groceries = groceries()

        val id = useCase()(draft().copy(categoryId = groceries.id, tagNames = listOf(" #Goa trip ", "goa-trip", ""))).getOrThrow()

        val saved = transactions.getTransaction(id).shouldNotBeNull()
        saved.amount shouldBe Money(-34_900L, Currency.INR)
        saved.postedLocalDate shouldBe LocalDate.of(2026, 9, 13) // 04:30Z is 10:00 IST
        saved.categoryId shouldBe groceries.id
        saved.categorySource shouldBe CategorySource.USER
        saved.needsReview shouldBe false
        saved.fingerprint shouldBe TransactionFingerprint.of(bank, saved.postedLocalDate, -34_900L, "Swiggy")
        saved.tags shouldHaveSize 1 // both spellings normalise to the same tag
        written.single().map { it.id } shouldContainExactly listOf(id)
    }

    @Test
    fun `without a chosen category the pipeline decides and low confidence is flagged for review`() = runBlocking {
        val groceries = groceries()
        val suggesting = CategorizationPipeline { CategorySuggestion(groceries.id, CategorySource.ON_DEVICE_MODEL, 0.6f) }

        val suggested = transactions.getTransaction(useCase(suggesting)(draft()).getOrThrow()).shouldNotBeNull()
        suggested.categoryId shouldBe groceries.id
        suggested.categorySource shouldBe CategorySource.ON_DEVICE_MODEL
        suggested.categoryConfidence shouldBe 0.6f
        suggested.needsReview shouldBe true

        val uncategorized = transactions.getTransaction(useCase()(draft()).getOrThrow()).shouldNotBeNull()
        uncategorized.categoryId shouldBe null
        uncategorized.categorySource shouldBe CategorySource.NONE
        uncategorized.isCategorized shouldBe false
    }

    @Test
    fun `FR_ACC_4 a transfer becomes two linked rows in the transfer category`() = runBlocking {
        val id = useCase()(draft(TransactionType.TRANSFER, 18_000_00L, "CC bill").copy(toAccountId = card)).getOrThrow()

        val out = transactions.getTransaction(id).shouldNotBeNull()
        val inn = transactions.getTransaction(out.transferPairId.shouldNotBeNull()).shouldNotBeNull()
        out.accountId shouldBe bank
        out.amount shouldBe Money(-18_000_00L, Currency.INR)
        inn.accountId shouldBe card
        inn.amount shouldBe Money(18_000_00L, Currency.INR)
        inn.transferPairId shouldBe out.id
        listOf(out, inn).forEach {
            it.type shouldBe TransactionType.TRANSFER
            it.categoryId shouldBe categories.byKey(DefaultTaxonomy.KEY_TRANSFER)?.id
        }
        written.single() shouldHaveSize 2
    }

    @Test
    fun `validation rejects zero amounts and transfers without a distinct target account`() = runBlocking {
        useCase()(draft(amount = 0L)).errorOrNull().shouldBeInstanceOf<AppError.Validation>().field shouldBe "amount"
        useCase()(draft(TransactionType.TRANSFER)).errorOrNull().shouldBeInstanceOf<AppError.Validation>().field shouldBe "toAccount"
        useCase()(draft(TransactionType.TRANSFER).copy(toAccountId = bank)).errorOrNull()
            .shouldBeInstanceOf<AppError.Validation>().field shouldBe "toAccount"
        transactions.saved shouldHaveSize 0
    }

    @Test
    fun `FR_TXN_2 editing keeps identity, model provenance and the pipeline is not re-run for an unchanged category`() = runBlocking {
        val groceries = groceries()
        val suggesting = CategorizationPipeline { CategorySuggestion(groceries.id, CategorySource.ON_DEVICE_MODEL, 0.9f) }
        val id = useCase(suggesting)(draft()).getOrThrow()
        val original = transactions.getTransaction(id).shouldNotBeNull()

        val edited = draft(amount = 40_000L, description = "Swiggy").copy(id = id, categoryId = groceries.id, notes = " lunch ")
        useCase()(edited).getOrThrow() shouldBe id

        val saved = transactions.getTransaction(id).shouldNotBeNull()
        saved.amount shouldBe Money(-40_000L, Currency.INR)
        saved.categorySource shouldBe CategorySource.ON_DEVICE_MODEL
        saved.categoryConfidence shouldBe 0.9f
        saved.notes shouldBe "lunch"
        saved.meta shouldBe original.meta
        saved.fingerprint shouldBe TransactionFingerprint.of(bank, saved.postedLocalDate, -40_000L, "Swiggy")

        // Choosing a different category is the user's call.
        val cafe = categories.byKey("food.cafe").shouldNotBeNull()
        useCase()(edited.copy(categoryId = cafe.id)).getOrThrow()
        transactions.getTransaction(id).shouldNotBeNull().categorySource shouldBe CategorySource.USER

        useCase()(edited.copy(id = TransactionId("missing"))) shouldBe
            Outcome.failure(AppError.NotFound("Transaction", "missing"))
    }

    @Test
    fun `frequent categories count only that type inside the window`() = runBlocking {
        val groceries = groceries()
        val cafe = categories.byKey("food.cafe").shouldNotBeNull()
        repeat(2) { useCase()(draft().copy(categoryId = groceries.id)).getOrThrow() }
        useCase()(draft().copy(categoryId = cafe.id)).getOrThrow()
        useCase()(draft(TransactionType.INCOME).copy(categoryId = cafe.id)).getOrThrow()
        useCase()(draft().copy(categoryId = cafe.id, postedAt = clock.now().minusSeconds(100L * 24 * 3600))).getOrThrow()

        val since = clock.today().minusDays(90)
        val spending = FakeSpendingRepository(transactions)
        spending.frequentCategories(TransactionType.EXPENSE, since, limit = 8) shouldContainExactly listOf(groceries.id, cafe.id)
        spending.frequentCategories(TransactionType.EXPENSE, since, limit = 1) shouldContainExactly listOf(groceries.id)
    }

    @Test
    fun `a blank narration falls back to the category name, then the type`() = runBlocking {
        val groceries = groceries()
        val named = transactions.getTransaction(useCase()(draft(description = "  ").copy(categoryId = groceries.id)).getOrThrow())
        named?.descriptionRaw shouldBe "Groceries"
        val typed = transactions.getTransaction(useCase()(draft(TransactionType.INCOME, description = "")).getOrThrow())
        typed?.descriptionRaw shouldBe "Income"
    }

    @Test
    fun `FR_TXN_11 duplicates are reported for new entries only`() = runBlocking {
        useCase()(draft()).getOrThrow()
        val again = draft()

        useCase().duplicatesOf(again) shouldHaveSize 1
        useCase().duplicatesOf(again.copy(amount = Money(1L, Currency.INR))) shouldHaveSize 0
        useCase().duplicatesOf(again.copy(id = transactions.saved.first().id)) shouldHaveSize 0
    }
}
