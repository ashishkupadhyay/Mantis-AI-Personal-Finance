package io.github.ashishkupadhyay.mantis.feature.accounts

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.time.LocalDate
import java.time.ZoneOffset

/** Fixed "today" for every accounts test: mid-September so both statement (18th) and due (6th) days are ahead. */
val TODAY: LocalDate = LocalDate.of(2026, 9, 13)

fun bankAccount(id: String = "bank", name: String = "HDFC Savings", archived: Boolean = false): Account = Account(
    id = AccountId(id),
    name = name,
    type = AccountType.BANK,
    currency = Currency.INR,
    openingBalance = Money.ofMajor(1_000, Currency.INR),
    openingDate = LocalDate.of(2026, 1, 1),
    institutionId = "hdfc",
    last4 = "4412",
    isArchived = archived,
    meta = testMeta(),
)

fun cardAccount(id: String = "card", statementDay: Int = 18, dueDay: Int = 6): Account = Account(
    id = AccountId(id),
    name = "ICICI Card",
    type = AccountType.CREDIT_CARD,
    currency = Currency.INR,
    openingBalance = Money.zero(Currency.INR),
    openingDate = LocalDate.of(2026, 1, 1),
    institutionId = "icici",
    last4 = "8831",
    icon = "credit_card",
    statementDay = statementDay,
    dueDay = dueDay,
    meta = testMeta(),
)

fun expense(accountId: AccountId, major: Long, on: LocalDate, excluded: Boolean = false): Transaction = Transaction(
    id = TransactionId(newId()),
    accountId = accountId,
    type = TransactionType.EXPENSE,
    amount = Money.ofMajor(-major, Currency.INR),
    postedAt = on.atStartOfDay().toInstant(ZoneOffset.UTC),
    postedLocalDate = on,
    descriptionRaw = "Expense $major",
    isExcluded = excluded,
    fingerprint = newId(),
    meta = testMeta(),
)
