package io.github.ashishkupadhyay.mantis.feature.importwizard

import app.cash.turbine.test
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
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeStatementImporter
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRequest
import io.github.ashishkupadhyay.mantis.core.domain.imports.RowDisposition
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.domain.usecase.ImportStatementUseCase
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-09-13T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val bank = account("bank", "HDFC Savings")
    private val wallet = account("wallet", "Paytm")
    private val accounts = FakeAccountRepository(listOf(bank, wallet))
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val transactions = FakeTransactionRepository()
    private val imports = FakeImportRepository(transactions)
    private val importer = FakeStatementImporter()
    private val messages = UserMessages()

    private fun account(id: String, name: String) =
        Account(AccountId(id), name, AccountType.BANK, Currency.INR, Money.zero(Currency.INR), LocalDate.of(2026, 1, 1), meta = testMeta())

    private fun useCase() = ImportStatementUseCase(
        imports, accounts, categories, RuleBasedCategorizationPipeline(categories),
        TransactionWriteObservers(emptySet(), NoOpLogger), clock, UuidV7(nowMillis = clock::epochMillis),
    )

    private fun vm(initial: String? = null) =
        ImportViewModel(initial?.let { FakeStatementImporter.source(it) }, importer, useCase(), accounts, messages)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_IMP_1_3_aRecognisedStatementSkipsMappingAndPreselectsTheFirstAccount() = runTest(dispatcher) {
        val vm = vm()
        vm.state.filter { it.accounts.isNotEmpty() }.first().let {
            it.step shouldBe ImportStep.SOURCE
            it.accountId shouldBe bank.id
            it.presets.map { p -> p.id } shouldContainExactly listOf("hdfc")
        }

        vm.onEvent(ImportEvent.SourceChosen(FakeStatementImporter.source("hdfc-sep.csv")))
        val mapped = vm.state.filter { it.step == ImportStep.ACCOUNT }.first()
        mapped.detectedPreset?.id shouldBe "hdfc"
        mapped.mappingComplete shouldBe true
        mapped.skippedMapping shouldBe true

        vm.onEvent(ImportEvent.Back)
        vm.state.value.step shouldBe ImportStep.SOURCE
    }

    @Test
    fun FR_IMP_4_anUnknownLayoutNeedsEveryRequiredRoleBeforeItMovesOn() = runTest(dispatcher) {
        val vm = vm(initial = "mystery.csv")
        val map = vm.state.filter { it.step == ImportStep.MAP }.first()
        map.detectedPreset.shouldBeNull()
        map.mappingComplete shouldBe false

        vm.onEvent(ImportEvent.Next)
        advanceUntilIdle()
        vm.state.value.step shouldBe ImportStep.MAP
        vm.state.value.error shouldBe "Map the date, description and amount columns first"

        vm.onEvent(ImportEvent.RoleChanged(0, ColumnRole.DATE))
        // Mapping the date column re-infers the pattern from its samples ("01/09/26").
        vm.state.value.mapping?.dateFormat shouldBe "dd/MM/yy"
        vm.state.value.sniff?.suggestedDateFormats shouldBe listOf("dd/MM/yy", "dd/MM/yyyy")
        vm.onEvent(ImportEvent.RoleChanged(1, ColumnRole.DESCRIPTION))
        vm.onEvent(ImportEvent.RoleChanged(2, ColumnRole.AMOUNT))
        // Moving a role to another column releases its previous column.
        vm.onEvent(ImportEvent.RoleChanged(1, ColumnRole.AMOUNT))
        vm.state.value.mapping?.roles shouldBe mapOf(0 to ColumnRole.DATE, 1 to ColumnRole.AMOUNT)
        vm.onEvent(ImportEvent.RoleChanged(2, ColumnRole.DESCRIPTION))
        vm.onEvent(ImportEvent.DateFormatChanged("yyyy-MM-dd"))
        vm.state.value.mappingComplete shouldBe true
        vm.state.value.error.shouldBeNull()

        vm.onEvent(ImportEvent.Next)
        vm.state.filter { it.step == ImportStep.ACCOUNT }.first()
        vm.onEvent(ImportEvent.Back)
        vm.state.value.step shouldBe ImportStep.MAP

        vm.onEvent(ImportEvent.PresetChosen("hdfc"))
        vm.state.value.mapping shouldBe importer.preset.mapping
        vm.state.value.detectedPreset?.id shouldBe "hdfc"
    }

    @Test
    fun FR_IMP_5_8_previewCountsThenCommitCategorizesAndUndoRemovesTheBatch() = runTest(dispatcher) {
        categories.saveRule(CategoryRule(RuleId("r"), RuleMatchType.CONTAINS, "swiggy", categoryId = delivery.id, meta = testMeta()))
        val vm = vm(initial = "hdfc-sep.csv")
        vm.state.filter { it.step == ImportStep.ACCOUNT }.first()

        vm.onEvent(ImportEvent.AccountChosen(wallet.id))
        vm.onEvent(ImportEvent.Next)
        val preview = vm.state.filter { it.step == ImportStep.PREVIEW }.first()
        importer.parses shouldBe 1
        preview.rows.size shouldBe 4
        preview.preview.shouldNotBeNull().let {
            it.total shouldBe 4
            it.valid shouldBe 3
            it.errors shouldBe 1
            it.duplicates shouldBe 0
            it.willImport shouldBe 3
            it.rows.map { r -> r.disposition } shouldContainExactly
                listOf(RowDisposition.IMPORT, RowDisposition.IMPORT, RowDisposition.IMPORT, RowDisposition.ERROR)
        }

        vm.onEvent(ImportEvent.Import)
        val done = vm.state.filter { it.step == ImportStep.IMPORT && !it.busy }.first()
        val result = done.result.shouldNotBeNull()
        result.imported shouldBe 3
        result.categorized shouldBe 1
        result.needsReview shouldBe 2
        result.errors shouldBe 1
        val saved = transactions.matching(TransactionFilter.ALL)
        saved.size shouldBe 3
        saved.all { it.accountId == wallet.id } shouldBe true
        imports.batch(result.batchId).shouldNotBeNull().presetId shouldBe "hdfc"

        messages.messages.test {
            vm.onEvent(ImportEvent.Undo)
            awaitItem().text shouldBe "Import undone · 3 transactions removed"
        }
        vm.state.value.undone shouldBe true
        transactions.matching(TransactionFilter.ALL).size shouldBe 0
    }

    @Test
    fun FR_IMP_6_reImportingShowsDuplicatesAndTheToggleLetsThemThrough() = runTest(dispatcher) {
        val rows = FakeStatementImporter.SAMPLE_ROWS
        useCase().commit(ImportRequest(FakeStatementImporter.source(), bank.id, null, rows, importDuplicates = false))
        val vm = vm(initial = "hdfc-sep.csv")
        vm.state.filter { it.step == ImportStep.ACCOUNT }.first()
        vm.onEvent(ImportEvent.Next)
        val preview = vm.state.filter { it.step == ImportStep.PREVIEW }.first().preview.shouldNotBeNull()
        preview.duplicates shouldBe 3
        preview.willImport shouldBe 0

        vm.onEvent(ImportEvent.Import)
        advanceUntilIdle()
        vm.state.value.step shouldBe ImportStep.PREVIEW
        vm.state.value.error.shouldNotBeNull()

        vm.onEvent(ImportEvent.ImportDuplicatesChanged(true))
        val forced = vm.state.filter { it.preview?.willImport == 3 }.first()
        forced.importDuplicates shouldBe true
        vm.onEvent(ImportEvent.Import)
        vm.state.filter { it.result != null }.first().result?.imported shouldBe 3
        transactions.matching(TransactionFilter.ALL).size shouldBe 6
    }

    @Test
    fun aFileTheSnifferRejectsStaysOnTheSourcePageWithTheReason() = runTest(dispatcher) {
        importer.failSniff = AppError.Validation("file", "The file is empty")
        val vm = vm(initial = "empty.csv")
        val failed = vm.state.filter { it.error != null }.first()
        failed.step shouldBe ImportStep.SOURCE
        failed.source.shouldBeNull()
        failed.error shouldBe "The file is empty"
    }
}
