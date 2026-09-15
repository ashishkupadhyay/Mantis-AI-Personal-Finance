package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.LocalDate

enum class AccountType { BANK, CREDIT_CARD, CASH, WALLET, INVESTMENT, LOAN }

/** Icon name (Material Symbols, resolved by `MantisIcons`) an account of this type gets until the user picks one. */
val AccountType.defaultIcon: String
    get() = when (this) {
        AccountType.BANK -> "account_balance"
        AccountType.CREDIT_CARD -> "credit_card"
        AccountType.CASH -> "payments"
        AccountType.WALLET -> "account_balance_wallet"
        AccountType.INVESTMENT -> "trending_up"
        AccountType.LOAN -> "request_quote"
    }

/**
 * A container of transactions (FR-ACC-1). Balance is derived: opening balance + Σ transactions (FR-ACC-2).
 * Credit-card accounts carry statement/due days (FR-ACC-5). `last4` is user-entered display data and is never
 * exposed through AppFunctions (FR-AFN-4).
 */
data class Account(
    val id: AccountId,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val openingBalance: Money,
    val openingDate: LocalDate,
    val institutionId: String? = null,
    val last4: String? = null,
    val colorSeed: Int = 0,
    val icon: String = "account_balance",
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val meta: SyncMeta,
) {
    init {
        require(name.isNotBlank()) { "Account name must not be blank" }
        require(openingBalance.currency.code == currency.code) { "Opening balance currency must match account currency" }
        require(last4 == null || (last4.length == LAST4_LENGTH && last4.all { it.isDigit() })) { "last4 must be 4 digits" }
        require(statementDay == null || statementDay in 1..MAX_DAY) { "statementDay out of range" }
        require(dueDay == null || dueDay in 1..MAX_DAY) { "dueDay out of range" }
    }

    /** Credit cards and loans are liabilities: their balance is shown as "owed" (FR-ACC-2). */
    val isLiability: Boolean get() = type == AccountType.CREDIT_CARD || type == AccountType.LOAN

    private companion object {
        const val LAST4_LENGTH = 4
        const val MAX_DAY = 31
    }
}
