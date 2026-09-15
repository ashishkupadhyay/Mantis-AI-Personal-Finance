package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An account with its derived balance (FR-ACC-2): opening balance + Σ live, posted transactions. */
data class AccountBalance(val account: Account, val balance: Money) {
    /** Credit cards and loans are shown as "owed": a negative balance is money the user owes. */
    val owed: Money? get() = if (account.isLiability && balance.minor < 0) -balance else null
}

/** Net position across accounts in one currency (FR-ACC-2): assets − liabilities. */
data class AccountTotals(val assets: Money, val liabilities: Money, val net: Money) {
    companion object {
        fun of(balances: List<AccountBalance>, currency: Currency): AccountTotals {
            val same = balances.filter { it.balance.currency.code == currency.code && !it.account.isArchived }
            val assets = Money(same.filter { !it.account.isLiability }.sumOf { it.balance.minor }, currency)
            val liabilities = Money(same.filter { it.account.isLiability }.sumOf { -it.balance.minor }, currency)
            return AccountTotals(assets, liabilities, assets - liabilities)
        }
    }
}

/**
 * The statement cycle a credit card is in (FR-ACC-5). Days are 1..28 so every month has them. The current cycle
 * runs from the day after the last statement to the next statement; the bill for the *previous* cycle is due on
 * [dueDate].
 */
data class BillingCycle(val cycleStart: LocalDate, val statementDate: LocalDate, val dueDate: LocalDate) {
    val cycle: DateRange get() = DateRange(cycleStart, statementDate)

    fun daysUntilDue(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)

    companion object {
        fun of(statementDay: Int, dueDay: Int, today: LocalDate): BillingCycle {
            val thisMonthStatement = today.withDayOfMonth(statementDay.coerceIn(1, MAX_DAY))
            val lastStatement = if (today.isAfter(thisMonthStatement)) thisMonthStatement else thisMonthStatement.minusMonths(1)
            val nextStatement = lastStatement.plusMonths(1)
            var due = lastStatement.withDayOfMonth(dueDay.coerceIn(1, MAX_DAY))
            if (!due.isAfter(lastStatement)) due = due.plusMonths(1)
            // Once the last bill's due date has passed, the next payment is for the statement still being built.
            if (due.isBefore(today)) due = due.plusMonths(1)
            return BillingCycle(cycleStart = lastStatement.plusDays(1), statementDate = nextStatement, dueDate = due)
        }

        private const val MAX_DAY = 28
    }
}
