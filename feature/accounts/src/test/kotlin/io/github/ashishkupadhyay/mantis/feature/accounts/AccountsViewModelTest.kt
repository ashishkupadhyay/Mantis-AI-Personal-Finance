package io.github.ashishkupadhyay.mantis.feature.accounts

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.kotest.matchers.collections.shouldContainExactly
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
class AccountsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = FakePreferencesRepository()
    private val accounts = FakeAccountRepository(listOf(bankAccount(), cardAccount(), bankAccount("old", "Old bank", archived = true)))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_ACC_2_splitsArchivedAccountsAndTotalsOnlyTheActiveOnes() = runTest(dispatcher) {
        accounts.balances.value = mapOf(
            AccountId("bank") to Money.ofMajor(84_250, Currency.INR),
            AccountId("card") to Money.ofMajor(-21_400, Currency.INR),
            AccountId("old") to Money.ofMajor(5_000, Currency.INR),
        )

        val state = AccountsViewModel(accounts, preferences).state.filter { it.loaded }.first()

        state.active.map { it.account.id.value } shouldContainExactly listOf("bank", "card")
        state.archived.map { it.account.id.value } shouldContainExactly listOf("old")
        state.totals?.assets shouldBe Money.ofMajor(84_250, Currency.INR)
        state.totals?.liabilities shouldBe Money.ofMajor(21_400, Currency.INR)
        state.totals?.net shouldBe Money.ofMajor(62_850, Currency.INR)
    }

    @Test
    fun FR_ACC_3_liabilityBalancesSurfaceAsOwed() = runTest(dispatcher) {
        accounts.balances.value = mapOf(AccountId("card") to Money.ofMajor(-21_400, Currency.INR))

        val state = AccountsViewModel(accounts, preferences).state.filter { it.loaded }.first()

        state.active.single { it.account.id.value == "card" }.owed shouldBe Money.ofMajor(21_400, Currency.INR)
        state.active.single { it.account.id.value == "bank" }.owed shouldBe null
    }
}
