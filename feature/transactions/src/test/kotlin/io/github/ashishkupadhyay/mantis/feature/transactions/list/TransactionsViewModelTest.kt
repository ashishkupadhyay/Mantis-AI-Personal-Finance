package io.github.ashishkupadhyay.mantis.feature.transactions.list

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeSpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTagRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.feature.transactions.BANK
import io.github.ashishkupadhyay.mantis.feature.transactions.CARD
import io.github.ashishkupadhyay.mantis.feature.transactions.TODAY
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val accounts = FakeAccountRepository(listOf(BANK, CARD))
    private val tags = FakeTagRepository()
    private val transactions = FakeTransactionRepository(
        listOf(
            transaction("ZOMATO ORDER", -710, TODAY, CARD, categoryId = delivery.id, id = "t1"),
            transaction("BLINKIT", -206, TODAY, BANK, id = "t2"),
            transaction("SALARY", 80_000, TODAY.minusDays(1), BANK, id = "t3"),
            transaction("CC PAYMENT", -18_000, TODAY.minusDays(7), BANK, id = "t4"),
            transaction("PAYMENT RECEIVED", 18_000, TODAY.minusDays(7), CARD, id = "t5"),
            transaction("AUGUST COFFEE", -120, TODAY.minusDays(20), BANK, id = "t6"),
        ),
    )
    private val messages = UserMessages()

    private fun viewModel(initialFilter: String? = null) = TransactionsViewModel(
        initialFilter, transactions, FakeSpendingRepository(transactions), categories, accounts, tags, messages,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_TXN_3_rowsAreGroupedUnderMonthBandsAndDayHeadersWithTotals() = runTest(dispatcher) {
        val items = viewModel().rows.asSnapshot()

        val kinds = items.map { it::class.simpleName }
        kinds.take(4) shouldContainExactly listOf("MonthHeader", "DayHeader", "Row", "Row")
        val month = items[0].shouldBeInstanceOf<TransactionListItem.MonthHeader>()
        month.month shouldBe YearMonth.of(2026, 9)
        month.total shouldBe Money.ofMajor(80_000 - 710 - 206, Currency.INR) // transfers are not counted, August is not this month
        val today = items[1].shouldBeInstanceOf<TransactionListItem.DayHeader>()
        today.day shouldBe TODAY
        today.total shouldBe Money.ofMajor(-916, Currency.INR)
        today.count shouldBe 2
        items.count { it is TransactionListItem.MonthHeader } shouldBe 2 // September and August
        items.count { it is TransactionListItem.Row } shouldBe 6
        val zomato = items.filterIsInstance<TransactionListItem.Row>().first { it.row.id == TransactionId("t1") }.row
        zomato.subtitle shouldBe "Food Delivery · ICICI Card"
    }

    @Test
    fun FR_TXN_4_searchIsDebouncedIntoTheFilter() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(TransactionsEvent.QueryChanged("z"))
        vm.onEvent(TransactionsEvent.QueryChanged("zom"))
        advanceTimeBy(100)
        vm.state.first { it.loaded }.filter.query shouldBe ""
        advanceTimeBy(400)

        vm.state.first { it.loaded }.filter.query shouldBe "zom"
        vm.rows.asSnapshot().filterIsInstance<TransactionListItem.Row>().map { it.row.title } shouldContainExactly listOf("ZOMATO ORDER")
    }

    @Test
    fun FR_TXN_5_filtersApplyAndLinksSeedTheInitialFilter() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(TransactionsEvent.FilterChanged(TransactionFilter(accountIds = setOf(CARD.id), types = setOf(TransactionType.EXPENSE))))
        vm.rows.asSnapshot().filterIsInstance<TransactionListItem.Row>().map { it.row.id.value } shouldContainExactly listOf("t1")
        vm.state.first { it.loaded && it.hasFilters }
        vm.onEvent(TransactionsEvent.ClearFilters)
        vm.state.first { it.loaded && !it.hasFilters }

        val linked = viewModel("category=food.delivery")
        linked.state.filter { it.loaded && it.filter.categoryIds.isNotEmpty() }.first().filter.categoryIds shouldBe setOf(delivery.id)
    }

    @Test
    fun FR_TXN_9_bulkActionsRunOnTheSelectionAndReport() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t1")))
        vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t2")))
        vm.state.first { it.loaded }.selection.size shouldBe 2

        messages.messages.test {
            vm.onEvent(TransactionsEvent.BulkRecategorize(delivery.id))
            awaitItem().text shouldBe "Updated 2 transactions"
            transactions.getTransaction(TransactionId("t2")).shouldNotBeNull().let {
                it.categoryId shouldBe delivery.id
                it.categorySource shouldBe CategorySource.USER
            }
            vm.state.first { it.loaded && it.selection.isEmpty() }

            vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t2")))
            vm.onEvent(TransactionsEvent.BulkTag("Goa"))
            awaitItem().text shouldBe "Updated 1 transaction"
            transactions.getTransaction(TransactionId("t2"))?.tags?.size shouldBe 1
        }
    }

    @Test
    fun FR_TXN_2_deleteOffersUndoThatRestoresTheRows() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t1")))
        vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t2")))

        messages.messages.test {
            vm.onEvent(TransactionsEvent.BulkDelete)
            val message = awaitItem()
            message.text shouldBe "Deleted 2 transactions"
            message.actionLabel shouldBe "Undo"
            transactions.matching(TransactionFilter.ALL).size shouldBe 4

            message.action.shouldNotBeNull().invoke()
            transactions.matching(TransactionFilter.ALL).size shouldBe 6

            vm.onEvent(TransactionsEvent.Delete(TransactionId("t6")))
            awaitItem().text shouldBe "Deleted 1 transaction"
        }
    }

    @Test
    fun FR_TXN_9_markTransferNeedsExactlyTwoMatchingRows() = runTest(dispatcher) {
        val vm = viewModel()
        messages.messages.test {
            vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t4")))
            vm.onEvent(TransactionsEvent.MarkTransfer)
            awaitItem().text shouldBe "Select exactly two rows to mark a transfer"

            vm.onEvent(TransactionsEvent.ToggleSelected(TransactionId("t5")))
            vm.onEvent(TransactionsEvent.MarkTransfer)
            awaitItem().text shouldBe "Marked as a transfer"
            transactions.getTransaction(TransactionId("t4"))?.transferPairId shouldBe TransactionId("t5")
            transactions.getTransaction(TransactionId("t5"))?.type shouldBe TransactionType.TRANSFER
            vm.state.first { it.loaded && it.selection.isEmpty() }
        }
    }
}
