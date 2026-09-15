package io.github.ashishkupadhyay.mantis.feature.transactions.add

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.log.NoOpLogger
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.categorization.NoCategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeSpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTagRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObservers
import io.github.ashishkupadhyay.mantis.core.domain.usecase.AddTransactionUseCase
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionPrefill
import io.github.ashishkupadhyay.mantis.feature.transactions.BANK
import io.github.ashishkupadhyay.mantis.feature.transactions.CARD
import io.github.ashishkupadhyay.mantis.feature.transactions.TODAY
import io.github.ashishkupadhyay.mantis.feature.transactions.ZONE
import io.github.ashishkupadhyay.mantis.feature.transactions.transaction
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(TODAY.atTime(18, 42).atZone(ZONE).toInstant(), ZONE)
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val groceries = categories.categories.value.values.first { it.key == "food.groceries" }
    private val accounts = FakeAccountRepository(listOf(BANK, CARD))
    private val tags = FakeTagRepository()
    private val transactions = FakeTransactionRepository(
        listOf(
            transaction("ZOMATO", -710, TODAY.minusDays(1), CARD, categoryId = delivery.id, id = "existing"),
            transaction("BLINKIT", -200, TODAY.minusDays(2), BANK, categoryId = groceries.id),
            transaction("BLINKIT", -300, TODAY.minusDays(3), BANK, categoryId = groceries.id),
        ),
    )
    private val messages = UserMessages()

    private fun viewModel(editId: String? = null, prefill: TransactionPrefill? = null): AddTransactionViewModel {
        val spending = FakeSpendingRepository(transactions)
        val useCase = AddTransactionUseCase(
            transactions, categories, tags, NoCategorizationPipeline,
            TransactionWriteObservers(emptySet(), NoOpLogger), clock, UuidV7(nowMillis = clock::epochMillis),
        )
        val lookups = EditorLookups(transactions, accounts, categories, tags, spending, clock)
        return AddTransactionViewModel(editId, prefill, useCase, lookups, clock, messages)
    }

    private fun AddTransactionViewModel.type(text: String) = text.forEach { onEvent(AddTransactionEvent.Key(KeypadKey.Digit(it))) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_TXN_1_newEntryDefaultsToNowFirstAccountAndLikelyCategoriesFirst() = runTest(dispatcher) {
        val state = viewModel().state.filter { it.loaded && it.likely.isNotEmpty() }.first()

        state.isNew shouldBe true
        state.type shouldBe TransactionType.EXPENSE
        state.date shouldBe TODAY
        state.time shouldBe LocalTime.of(18, 42)
        state.accountId shouldBe BANK.id
        state.currency shouldBe Currency.INR
        state.likely.take(2).map { it.id } shouldContainExactly listOf(groceries.id, delivery.id)
        state.likely.size shouldBe 8
    }

    @Test
    fun FR_TXN_1_threeTapsAmountChipSaveWritesAnExpense() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()
        vm.type("349")
        vm.onEvent(AddTransactionEvent.CategoryChanged(delivery.id))

        vm.effects.test {
            vm.onEvent(AddTransactionEvent.Save())
            val saved = awaitItem().shouldBeInstanceOf<AddTransactionEffect.Saved>()
            saved.addAnother shouldBe false
            val row = transactions.getTransaction(saved.id).shouldNotBeNull()
            row.amount shouldBe Money.ofMajor(-349, Currency.INR)
            row.categoryId shouldBe delivery.id
            row.categorySource shouldBe CategorySource.USER
            row.accountId shouldBe BANK.id
            row.postedLocalDate shouldBe TODAY
            row.descriptionRaw shouldBe "Food Delivery"
        }
    }

    @Test
    fun validationBlocksSavingWithoutAnAmountOrTransferTarget() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()
        vm.onEvent(AddTransactionEvent.Save())
        vm.state.value.amountError shouldBe true

        vm.type("50")
        vm.onEvent(AddTransactionEvent.TypeChanged(TransactionType.TRANSFER))
        vm.onEvent(AddTransactionEvent.ToAccountChanged(BANK.id)) // same as the source
        vm.onEvent(AddTransactionEvent.Save())
        vm.state.value.accountError shouldBe true
        transactions.saved.size shouldBe 0

        vm.onEvent(AddTransactionEvent.ToAccountChanged(CARD.id))
        vm.effects.test {
            vm.onEvent(AddTransactionEvent.Save())
            awaitItem().shouldBeInstanceOf<AddTransactionEffect.Saved>()
        }
        transactions.saved.size shouldBe 2 // both legs of the transfer
    }

    @Test
    fun FR_TXN_11_duplicatesAskFirstAndSaveAnywayProceeds() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()
        vm.onEvent(AddTransactionEvent.AccountChanged(CARD.id))
        vm.onEvent(AddTransactionEvent.DateChanged(TODAY.minusDays(1)))
        vm.type("710")

        vm.effects.test {
            vm.onEvent(AddTransactionEvent.Save())
            val warning = awaitItem().shouldBeInstanceOf<AddTransactionEffect.PossibleDuplicate>()
            warning.duplicates.single().descriptionRaw shouldBe "ZOMATO"
            transactions.saved.size shouldBe 0

            vm.onEvent(AddTransactionEvent.Save(ignoreDuplicates = true))
            awaitItem().shouldBeInstanceOf<AddTransactionEffect.Saved>()
            transactions.saved.size shouldBe 1
        }
    }

    @Test
    fun saveAndAddAnotherKeepsTheSheetWithAFreshAmount() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()
        vm.type("120")
        vm.onEvent(AddTransactionEvent.DescriptionChanged("Coffee"))
        vm.onEvent(AddTransactionEvent.TagInputChanged("#work"))
        vm.onEvent(AddTransactionEvent.CommitTag)

        vm.effects.test {
            vm.onEvent(AddTransactionEvent.Save(addAnother = true))
            awaitItem().shouldBeInstanceOf<AddTransactionEffect.Saved>().addAnother shouldBe true
        }
        val state = vm.state.value
        state.amount.isEmpty shouldBe true
        state.description shouldBe ""
        state.tags.size shouldBe 0
        state.accountId shouldBe BANK.id
        transactions.saved.single().tags.size shouldBe 1
    }

    @Test
    fun FR_TXN_2_editingLoadsTheRowAndSavesInPlace() = runTest(dispatcher) {
        val vm = viewModel(editId = "existing")
        val loaded = vm.state.filter { it.loaded }.first()

        loaded.isNew shouldBe false
        loaded.amount.display shouldBe "710"
        loaded.accountId shouldBe CARD.id
        loaded.categoryId shouldBe delivery.id
        loaded.description shouldBe "ZOMATO"
        loaded.date shouldBe TODAY.minusDays(1)

        vm.onEvent(AddTransactionEvent.Key(KeypadKey.Backspace))
        vm.onEvent(AddTransactionEvent.NotesChanged("late dinner"))
        vm.effects.test {
            vm.onEvent(AddTransactionEvent.Save())
            awaitItem().shouldBeInstanceOf<AddTransactionEffect.Saved>().id.value shouldBe "existing"
        }
        val row = transactions.getTransaction(loaded.editId.shouldNotBeNull()).shouldNotBeNull()
        row.amount shouldBe Money.ofMajor(-71, Currency.INR)
        row.notes shouldBe "late dinner"
        row.categorySource shouldBe CategorySource.ON_DEVICE_MODEL // unchanged category keeps its provenance
    }

    @Test
    fun prefillSeedsTypeAmountMerchantAndCategoryByKey() = runTest(dispatcher) {
        val prefill = TransactionPrefill(
            amountMinor = 25_000L, merchant = "Uber", categoryKey = "food.delivery", accountId = "card", type = "INCOME",
        )
        val state = viewModel(prefill = prefill).state.filter { it.loaded }.first()

        state.type shouldBe TransactionType.INCOME
        state.amount.display shouldBe "250"
        state.description shouldBe "Uber"
        state.categoryId shouldBe delivery.id
        state.accountId shouldBe CARD.id
        advanceUntilIdle()
    }
}
