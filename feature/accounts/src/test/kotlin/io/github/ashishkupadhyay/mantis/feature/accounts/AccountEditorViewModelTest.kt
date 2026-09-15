package io.github.ashishkupadhyay.mantis.feature.accounts

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeAccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
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
class AccountEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(TODAY.atStartOfDay(zone).toInstant(), zone)
    private val preferences = FakePreferencesRepository(UserPreferences(currencyCode = "USD"))
    private val accounts = FakeAccountRepository(listOf(cardAccount()))

    private fun viewModel(id: String? = null) =
        AccountEditorViewModel(id, accounts, preferences, clock, UuidV7(nowMillis = clock::epochMillis))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun FR_ACC_1_newAccountStartsFromPreferencesAndTheFirstOfThisMonth() = runTest(dispatcher) {
        val state = viewModel().state.filter { it.loaded }.first()

        state.isNew shouldBe true
        state.currencyCode shouldBe "USD"
        state.openingDate shouldBe LocalDate.of(2026, 9, 1)
        state.type shouldBe AccountType.BANK
    }

    @Test
    fun FR_ACC_1_saveValidatesEveryFieldBeforeWriting() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()

        vm.onEvent(AccountEditorEvent.Last4Changed("12a"))
        vm.onEvent(AccountEditorEvent.OpeningBalanceChanged("12,3.4.5"))
        vm.onEvent(AccountEditorEvent.Save)
        advanceUntilIdle()

        val state = vm.state.value
        state.nameError shouldBe true
        state.last4Error shouldBe true
        state.last4 shouldBe "12"
        state.balanceError shouldBe true
        accounts.accounts.value.size shouldBe 1

        vm.onEvent(AccountEditorEvent.NameChanged("  Cash  "))
        state.nameError shouldBe true // the snapshot is immutable; the live state clears the flag
        vm.state.value.nameError shouldBe false
    }

    @Test
    fun FR_ACC_1_savingANewBankAccountConvertsMajorUnitsAndDropsCardOnlyFields() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.filter { it.loaded }.first()

        vm.onEvent(AccountEditorEvent.NameChanged("  Wallet  "))
        vm.onEvent(AccountEditorEvent.TypeChanged(AccountType.WALLET))
        vm.onEvent(AccountEditorEvent.InstitutionChanged("paytm"))
        vm.onEvent(AccountEditorEvent.CurrencyChanged("INR"))
        vm.onEvent(AccountEditorEvent.OpeningBalanceChanged("1,250.50"))
        vm.onEvent(AccountEditorEvent.OpeningDateChanged(LocalDate.of(2026, 3, 1)))
        vm.onEvent(AccountEditorEvent.StatementDayChanged(10))

        vm.effects.test {
            vm.onEvent(AccountEditorEvent.Save)
            val saved = awaitItem().shouldBeInstanceOf<AccountEditorEffect.Saved>()
            val account = accounts.accounts.value.getValue(saved.id)
            account.name shouldBe "Wallet"
            account.type shouldBe AccountType.WALLET
            account.institutionId shouldBe "paytm"
            account.currency shouldBe Currency.INR
            account.openingBalance shouldBe Money(125_050L, Currency.INR)
            account.openingDate shouldBe LocalDate.of(2026, 3, 1)
            account.last4 shouldBe null
            account.statementDay shouldBe null
            account.dueDay shouldBe null
            account.sortOrder shouldBe 1 // after the seeded card
            account.icon shouldBe "account_balance_wallet" // untouched icon follows the type
            account.meta shouldBe SyncMeta.unstamped() // the data layer stamps it inside the write transaction
        }
    }

    @Test
    fun FR_ACC_5_editingACardKeepsItsIdentityAndCycleDays() = runTest(dispatcher) {
        val vm = viewModel("card")
        val loaded = vm.state.filter { it.loaded }.first()

        loaded.isNew shouldBe false
        loaded.name shouldBe "ICICI Card"
        loaded.isCard shouldBe true
        loaded.statementDay shouldBe 18
        loaded.dueDay shouldBe 6
        loaded.last4 shouldBe "8831"

        vm.onEvent(AccountEditorEvent.DueDayChanged(40))
        vm.onEvent(AccountEditorEvent.NameChanged("ICICI Amazon"))
        vm.effects.test {
            vm.onEvent(AccountEditorEvent.Save)
            awaitItem().shouldBeInstanceOf<AccountEditorEffect.Saved>().id shouldBe AccountId("card")
        }
        val account = accounts.accounts.value.getValue(AccountId("card"))
        account.name shouldBe "ICICI Amazon"
        account.dueDay shouldBe 28
        account.statementDay shouldBe 18
        account.meta shouldBe cardAccount().meta
    }

    @Test
    fun changingTypeDropsAnInstitutionThatDoesNotOfferTheNewType() = runTest(dispatcher) {
        val vm = viewModel("card")
        vm.state.filter { it.loaded }.first()

        vm.onEvent(AccountEditorEvent.TypeChanged(AccountType.BANK))
        vm.state.value.institutionId shouldBe "icici"

        vm.onEvent(AccountEditorEvent.TypeChanged(AccountType.CASH))
        vm.state.value.institutionId shouldBe null
    }
}
