package io.github.ashishkupadhyay.mantis.core.model

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import java.time.LocalDate

/** An inclusive calendar-date range in the user's zone (period queries run on `postedLocalDate`). */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "DateRange end before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    val days: Long get() = endInclusive.toEpochDay() - start.toEpochDay() + 1
}

enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

/**
 * Everything the transactions list can filter on (FR-TXN-4/5, FR-VIEW-7). Empty sets mean "any". The repository
 * turns it into a parameterised query — never string concatenation (NFR-20c); [query] is tokenised for FTS.
 */
data class TransactionFilter(
    val accountIds: Set<AccountId> = emptySet(),
    val categoryIds: Set<CategoryId> = emptySet(),
    val tagIds: Set<TagId> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
    val dateRange: DateRange? = null,
    val query: String = "",
    val includeExcluded: Boolean = true,
    val needsReviewOnly: Boolean = false,
    /** Only rows without a category (FR-TXN-5 "uncategorized only"). */
    val uncategorizedOnly: Boolean = false,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val sort: TransactionSort = TransactionSort.DATE_DESC,
) {
    val isEmpty: Boolean
        get() = accountIds.isEmpty() && categoryIds.isEmpty() && tagIds.isEmpty() && types.isEmpty() && dateRange == null &&
            query.isBlank() && includeExcluded && !needsReviewOnly && !uncategorizedOnly && minAmountMinor == null &&
            maxAmountMinor == null

    companion object {
        val ALL = TransactionFilter()
    }
}

/**
 * What a budget counts: counted expenses (never transfers, excluded or deleted rows) posted in [range], narrowed to
 * [categoryIds] and [accountIds] when those are non-empty (FR-BUD-1 "all spending" = empty sets).
 */
data class SpendScope(
    val range: DateRange,
    val categoryIds: Set<CategoryId> = emptySet(),
    val accountIds: Set<AccountId> = emptySet(),
) {
    fun covers(transaction: Transaction): Boolean =
        transaction.type == TransactionType.EXPENSE && !transaction.isExcluded && !transaction.meta.isDeleted &&
            transaction.postedLocalDate in range &&
            (categoryIds.isEmpty() || transaction.categoryId in categoryIds) &&
            (accountIds.isEmpty() || transaction.accountId in accountIds)
}

/** Spend attributed to one category over a period (doc 02 §5.3). */
data class CategorySpend(val categoryId: CategoryId?, val total: Money, val count: Int)

/** Signed total of one calendar day ([count] rows contributed; 0 when the query did not count). */
data class DayTotal(val day: LocalDate, val total: Money, val count: Int = 0)
