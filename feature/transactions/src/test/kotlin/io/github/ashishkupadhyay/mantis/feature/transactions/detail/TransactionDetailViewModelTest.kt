package io.github.ashishkupadhyay.mantis.feature.transactions.detail

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTagRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.feature.transactions.BANK
import io.github.ashishkupadhyay.mantis.feature.transactions.CARD
import io.github.ashishkupadhyay.mantis.feature.transactions.TODAY
import io.github.ashishkupadhyay.mantis.feature.transactions.transaction
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository.withDefaults()
    private val delivery = categories.categories.value.values.first { it.key == "food.delivery" }
    private val accounts = FakeAccountRepository(listOf(BANK, CARD))
    private val tags = FakeTagRepository()
    private val transactions = FakeTransactionRepository(
        listOf(
            transaction("ZOMATO", -710, TODAY, CARD, categoryId = delivery.id, id = "t1"),
            transaction("CC PAYMENT", -18_000, TODAY, BANK, type = TransactionType.TRANSFER, id = "out")
                .copy(transferPairId = TransactionId("in")),
            transaction("PAYMENT RECEIVED", 18_000, TODAY, CARD, type = TransactionType.TRANSFER, id = "in")
                .copy(transferPairId = TransactionId("out")),
        ),
    )
    private val messages = UserMessages()

    private fun viewModel(id: String) = TransactionDetailViewModel(id, transactions, categories, accounts, tags, messages)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun resolvesCategoryGroupAccountAndTransferPair() = runTest(dispatcher) {
        val state = viewModel("t1").state.filter { it.loaded }.first()
        state.category?.id shouldBe delivery.id
        state.categoryGroup?.key shouldBe "food"
        state.account shouldBe CARD
        state.pairAccount shouldBe null

        val transfer = viewModel("out").state.filter { it.loaded }.first()
        transfer.account shouldBe BANK
        transfer.pairAccount shouldBe CARD

        viewModel("missing").state.filter { it.loaded }.first().missing shouldBe true
    }

    @Test
    fun FR_TXN_12_correctionIsRecordedAsTheUsersChoice() = runTest(dispatcher) {
        val vm = viewModel("t1")
        vm.state.filter { it.loaded }.first()
        val groceries = categories.categories.value.values.first { it.key == "food.groceries" }

        vm.onEvent(TransactionDetailEvent.SetCategory(groceries.id))
        vm.state.filter { it.category?.id == groceries.id }.first().transaction?.categorySource shouldBe CategorySource.USER

        vm.onEvent(TransactionDetailEvent.SetExcluded(true))
        vm.state.filter { it.transaction?.isExcluded == true }.first()
    }

    @Test
    fun FR_TXN_2_deleteNavigatesBackAndOffersUndo() = runTest(dispatcher) {
        val vm = viewModel("t1")
        messages.messages.test {
            vm.effects.test {
                vm.onEvent(TransactionDetailEvent.Delete)
                awaitItem() shouldBe TransactionDetailEffect.Deleted
            }
            val message = awaitItem()
            message.actionLabel shouldBe "Undo"
            transactions.getTransaction(TransactionId("t1")) shouldBe null
            message.action.shouldNotBeNull().invoke()
            transactions.getTransaction(TransactionId("t1")).shouldNotBeNull()
        }
    }
}
