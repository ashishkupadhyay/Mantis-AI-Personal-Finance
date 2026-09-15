package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AccountBalanceTest {

    private fun account(name: String, type: AccountType) = Account(
        id = AccountId(name), name = name, type = type, currency = Currency.INR, openingBalance = Money.zero(Currency.INR),
        openingDate = LocalDate.of(2026, 1, 1), meta = SyncMeta.local(1L, DeviceId("d")),
    )

    @Test
    fun `FR_ACC_2 totals split assets and liabilities and show cards as owed`() {
        val bank = AccountBalance(account("bank", AccountType.BANK), Money.ofMajor(1000, Currency.INR))
        val card = AccountBalance(account("card", AccountType.CREDIT_CARD), Money.ofMajor(-300, Currency.INR))
        val archived = AccountBalance(account("old", AccountType.BANK).copy(isArchived = true), Money.ofMajor(999, Currency.INR))

        val totals = AccountTotals.of(listOf(bank, card, archived), Currency.INR)

        totals.assets shouldBe Money.ofMajor(1000, Currency.INR)
        totals.liabilities shouldBe Money.ofMajor(300, Currency.INR)
        totals.net shouldBe Money.ofMajor(700, Currency.INR)
        card.owed shouldBe Money.ofMajor(300, Currency.INR)
        bank.owed shouldBe null
    }

    @Test
    fun `FR_ACC_5 billing cycle runs statement to statement with the due date after the last statement`() {
        // Statement on the 18th, due on the 6th; today is 13 September → cycle 19 Aug .. 18 Sep, bill due 6 Sep already passed → next 6 Oct.
        val cycle = BillingCycle.of(statementDay = 18, dueDay = 6, today = LocalDate.of(2026, 9, 13))
        cycle.cycleStart shouldBe LocalDate.of(2026, 8, 19)
        cycle.statementDate shouldBe LocalDate.of(2026, 9, 18)
        cycle.dueDate shouldBe LocalDate.of(2026, 10, 6)
        cycle.daysUntilDue(LocalDate.of(2026, 9, 13)) shouldBe 23

        // On the due day itself it is still "due today", not rolled forward.
        BillingCycle.of(statementDay = 18, dueDay = 6, today = LocalDate.of(2026, 9, 6)).dueDate shouldBe LocalDate.of(2026, 9, 6)

        // After the statement date the cycle rolls forward and the due date is in the coming month.
        val later = BillingCycle.of(statementDay = 18, dueDay = 6, today = LocalDate.of(2026, 9, 20))
        later.cycleStart shouldBe LocalDate.of(2026, 9, 19)
        later.dueDate shouldBe LocalDate.of(2026, 10, 6)
        later.daysUntilDue(LocalDate.of(2026, 9, 20)) shouldBe 16
    }
}
