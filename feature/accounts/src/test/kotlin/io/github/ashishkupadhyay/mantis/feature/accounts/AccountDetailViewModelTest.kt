package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.kotest.matchers.collections.shouldContainExactly
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(TODAY.atStartOfDay(zone).toInstant(), zone)
    private val bank = bankAccount()
    private val card = cardAccount()
    private val accounts = FakeAccountRepository(listOf(bank, card))
    private val categories = FakeCategoryRepository.withDefaults()
    private val transactions = FakeTransactionRepository(
        listOf(
            expense(card.id, 1_200, LocalDate.of(2026, 8, 25)),
            expense(card.id, 800, LocalDate.of(2026, 9, 10)),
            expense(card.id, 5_000, LocalDate.of(2026, 9, 11), excluded = true),
            expense(card.id, 999, LocalDate.of(2026, 8, 10)),
            expense(bank.id, 300, LocalDate.of(2026, 9, 12)),
        ),
    )

    private fun viewModel(id: String) = AccountDetailViewModel(id, accounts, transactions, categories, clock)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_ACC_5_cardCycleSumsOnlyCountedExpensesInsideTheCurrentStatementCycle() = runTest(dispatcher) {
        accounts.balances.value = mapOf(card.id to Money.ofMajor(-2_000, Currency.INR))

        val state = viewModel("card").state.filter { it.loaded }.first()

        state.account shouldBe card
        state.balance shouldBe Money.ofMajor(-2_000, Currency.INR)
        val cycle = state.card ?: error("card facts expected")
        cycle.cycle.cycleStart shouldBe LocalDate.of(2026, 8, 19)
        cycle.cycle.statementDate shouldBe LocalDate.of(2026, 9, 18)
        cycle.cycleSpend shouldBe Money.ofMajor(-2_000, Currency.INR)
        cycle.daysUntilStatement shouldBe 5
        cycle.daysUntilDue shouldBe 23
    }

    @Test
    fun bankAccountsHaveNoCycleFactsAndOnlyTheirOwnRows() = runTest(dispatcher) {
        val vm = viewModel("bank")

        val state = vm.state.filter { it.loaded }.first()
        state.card shouldBe null

        val rows = vm.rows.asSnapshot()
        rows.map { it.subtitle } shouldContainExactly listOf("Uncategorized · ${bank.name}")
    }

    @Test
    fun cardRowsAreNewestFirstWithCategoryAndAccountNamesResolved() = runTest(dispatcher) {
        val rows = viewModel("card").rows.asSnapshot()

        rows.map { it.title } shouldContainExactly listOf("Expense 5000", "Expense 800", "Expense 1200", "Expense 999")
        rows.all { it.subtitle.endsWith(card.name) } shouldBe true
    }

    @Test
    fun missingAccountIsReportedRatherThanCrashing() = runTest(dispatcher) {
        val state = viewModel("nope").state.filter { it.loaded }.first()
        state.missing shouldBe true
        state.account shouldBe null
    }

    @Test
    fun FR_ACC_4_archiveTogglesAndDeleteReportsTheRemovedRowCount() = runTest(dispatcher) {
        accounts.transactionCount = { id -> transactions.matching(TransactionFilter(accountIds = setOf(id))).size }
        val vm = viewModel("card")

        vm.onEvent(AccountDetailEvent.SetArchived(true))
        advanceUntilIdle()
        accounts.accounts.value[AccountId("card")]?.isArchived shouldBe true

        vm.effects.test {
            vm.onEvent(AccountDetailEvent.Delete)
            awaitItem().shouldBeInstanceOf<AccountDetailEffect.Deleted>().removedTransactions shouldBe 4
            vm.onEvent(AccountDetailEvent.Delete)
            awaitItem().shouldBeInstanceOf<AccountDetailEffect.Message>()
        }
    }
}
