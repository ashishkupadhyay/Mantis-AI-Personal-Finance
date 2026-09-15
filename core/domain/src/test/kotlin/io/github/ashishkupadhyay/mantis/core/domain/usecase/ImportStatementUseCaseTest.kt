package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.log.NoOpLogger
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleBasedCategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeImportRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.domain.imports.ByteSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRequest
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.RowDisposition
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.EntrySource
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ImportStatementUseCaseTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(Instant.parse("2026-09-13T04:30:00Z"), zone)
    private val bank = Account(
        AccountId("bank"), "HDFC", AccountType.BANK, Currency.INR, Money.zero(Currency.INR), LocalDate.of(2026, 1, 1), meta = testMeta(),
    )
    private val accounts = FakeAccountRepository(listOf(bank))
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val transactions = FakeTransactionRepository()
    private val imports = FakeImportRepository(transactions)
    private val source = ImportSource("hdfc.csv", 3, ByteSource { ByteArrayInputStream("abc".toByteArray()) })

    private fun useCase() = ImportStatementUseCase(
        imports, accounts, categories, RuleBasedCategorizationPipeline(categories),
        TransactionWriteObservers(emptySet(), NoOpLogger), clock, UuidV7(nowMillis = clock::epochMillis),
    )

    private fun row(line: Int, day: Int, minor: Long, description: String, category: String? = null) =
        ParsedRow(line, listOf(description), LocalDate.of(2026, 9, day), description, minor, categoryName = category)

    private val rows = listOf(
        row(1, 1, -349_00, "UPI-SWIGGY-ORDER"),
        row(2, 2, 80_000_00, "SALARY ACME", category = "Salary"),
        row(3, 3, -1_250_50, "AMAZON.IN"),
        ParsedRow(4, listOf("bad"), error = "Unreadable date “bad”"),
    )

    @Test
    fun `FR_IMP_7 FR_IMP_8 a batch imports valid rows, categorizes them and can be undone exactly`() = runBlocking {
        categories.saveRule(CategoryRule(RuleId("r"), RuleMatchType.CONTAINS, "swiggy", categoryId = delivery.id, meta = testMeta()))

        val result = useCase().commit(ImportRequest(source, bank.id, "hdfc", rows, importDuplicates = false)).getOrThrow()

        result.imported shouldBe 3
        result.errors shouldBe 1
        result.duplicates shouldBe 0
        result.categorized shouldBe 2 // the rule and the Category column; Amazon stays uncategorized
        result.needsReview shouldBe 1
        val saved = transactions.matching(TransactionFilter.ALL).sortedBy { it.postedLocalDate }
        saved.map { it.amount.minor } shouldContainExactly listOf(-349_00L, 80_000_00L, -1_250_50L)
        saved[0].let {
            it.categoryId shouldBe delivery.id
            it.categorySource shouldBe CategorySource.USER_RULE
            it.entrySource shouldBe EntrySource.IMPORT
            it.importBatchId shouldBe result.batchId
            it.type shouldBe TransactionType.EXPENSE
            it.postedLocalDate shouldBe LocalDate.of(2026, 9, 1)
        }
        saved[1].categorySource shouldBe CategorySource.IMPORT
        saved[1].type shouldBe TransactionType.INCOME
        val batch = imports.batch(result.batchId).shouldNotBeNull()
        batch.rowCount shouldBe 4
        batch.importedCount shouldBe 3
        batch.fileHash.length shouldBe 64

        useCase().undo(result.batchId) shouldBe 3
        transactions.matching(TransactionFilter.ALL).size shouldBe 0
        imports.batch(result.batchId)?.isUndone shouldBe true
    }

    @Test
    fun `FR_IMP_6 re-importing the same file finds only duplicates unless the user insists`() = runBlocking {
        useCase().commit(ImportRequest(source, bank.id, null, rows, importDuplicates = false)).getOrThrow()

        val preview = useCase().preview(bank.id, rows, importDuplicates = false)
        preview.total shouldBe 4
        preview.valid shouldBe 3
        preview.duplicates shouldBe 3
        preview.errors shouldBe 1
        preview.willImport shouldBe 0
        preview.rows.map { it.disposition } shouldContainExactly
            listOf(RowDisposition.DUPLICATE, RowDisposition.DUPLICATE, RowDisposition.DUPLICATE, RowDisposition.ERROR)

        useCase().commit(ImportRequest(source, bank.id, null, rows, importDuplicates = false))
            .errorOrNull().shouldBeInstanceOf<AppError.Validation>()

        val forced = useCase().commit(ImportRequest(source, bank.id, null, rows, importDuplicates = true)).getOrThrow()
        forced.imported shouldBe 3
        transactions.matching(TransactionFilter.ALL).size shouldBe 6
    }

    @Test
    fun `a value-date shift of one day still counts as the same row`() = runBlocking {
        useCase().commit(ImportRequest(source, bank.id, null, rows.take(1), importDuplicates = false)).getOrThrow()

        val shifted = listOf(row(1, 2, -349_00, "upi-swiggy-order"))
        useCase().preview(bank.id, shifted, importDuplicates = false).duplicates shouldBe 1
        useCase().preview(AccountId("other"), shifted, importDuplicates = false).duplicates shouldBe 0
    }
}
