package io.github.ashishkupadhyay.mantis.feature.transactions

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/** Fixed "today" for the transactions tests (a Sunday). */
val TODAY: LocalDate = LocalDate.of(2026, 9, 13)
val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

val BANK = Account(
    id = AccountId("bank"), name = "HDFC Savings", type = AccountType.BANK, currency = Currency.INR,
    openingBalance = Money.ofMajor(1_000, Currency.INR), openingDate = LocalDate.of(2026, 1, 1), meta = testMeta(),
)

val CARD = Account(
    id = AccountId("card"), name = "ICICI Card", type = AccountType.CREDIT_CARD, currency = Currency.INR,
    openingBalance = Money.zero(Currency.INR), openingDate = LocalDate.of(2026, 1, 1), statementDay = 18, dueDay = 6, meta = testMeta(),
)

fun transaction(
    description: String,
    major: Long,
    on: LocalDate,
    account: Account = BANK,
    type: TransactionType = if (major < 0) TransactionType.EXPENSE else TransactionType.INCOME,
    categoryId: CategoryId? = null,
    id: String = newId(),
): Transaction = Transaction(
    id = TransactionId(id),
    accountId = account.id,
    type = type,
    amount = Money.ofMajor(major, Currency.INR),
    postedAt = on.atTime(12, 0).atZone(ZONE).toInstant(),
    postedLocalDate = on,
    descriptionRaw = description,
    categoryId = categoryId,
    categorySource = if (categoryId == null) CategorySource.NONE else CategorySource.ON_DEVICE_MODEL,
    categoryConfidence = if (categoryId == null) null else 0.9f,
    fingerprint = newId(),
    meta = testMeta(),
)
