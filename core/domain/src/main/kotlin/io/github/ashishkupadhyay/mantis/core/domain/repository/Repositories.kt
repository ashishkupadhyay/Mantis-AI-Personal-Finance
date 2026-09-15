package io.github.ashishkupadhyay.mantis.core.domain.repository

import androidx.paging.PagingData
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountBalance
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.CategorySpend
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.DayTotal
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.SpendScope
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
 * Repository contracts (doc 02 §4.3): features and use-cases see only these; `core:data` implements them over Room
 * and DataStore and writes the sync outbox in the same transaction as every change.
 */

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences
}

interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    fun observeAccount(id: AccountId): Flow<Account?>

    suspend fun getAccount(id: AccountId): Account?

    /** Opening balance + Σ live, posted transactions (FR-ACC-2). */
    fun observeBalance(id: AccountId): Flow<Money>

    /** Every account with its balance in one query round-trip (account cards, totals). */
    fun observeBalances(includeArchived: Boolean = false): Flow<List<AccountBalance>>

    suspend fun save(account: Account)

    suspend fun setArchived(id: AccountId, archived: Boolean)

    /** Soft-deletes the account and every transaction on it (FR-ACC-4); the UI confirms first. Returns rows removed. */
    suspend fun delete(id: AccountId): Outcome<Int>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>

    suspend fun categories(): List<Category>

    suspend fun getCategory(id: CategoryId): Category?

    suspend fun byKey(key: String): Category?

    suspend fun save(category: Category)

    /** One write for several rows — reordering a group's leaves (FR-CAT-1). */
    suspend fun saveAll(categories: List<Category>)

    suspend fun setHidden(id: CategoryId, hidden: Boolean)

    /** User categories only; system rows are hidden, never deleted. */
    suspend fun delete(id: CategoryId): Outcome<Unit>

    /** Seeds the bundled taxonomy if it is missing (FR-ONB-8); idempotent. */
    suspend fun ensureDefaults()

    fun observeRules(): Flow<List<CategoryRule>>

    suspend fun rules(): List<CategoryRule>

    suspend fun saveRule(rule: CategoryRule)

    suspend fun deleteRule(id: RuleId)
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>

    suspend fun tags(ids: Set<TagId>): List<Tag>

    /** Returns the existing tag with the same normalised name, or creates one (FR-VIEW-4). */
    suspend fun findOrCreate(name: String): Tag

    suspend fun save(tag: Tag)

    suspend fun delete(id: TagId)
}

interface TransactionRepository {
    fun observeTransaction(id: TransactionId): Flow<Transaction?>

    suspend fun getTransaction(id: TransactionId): Transaction?

    fun pagedTransactions(filter: TransactionFilter): Flow<PagingData<Transaction>>

    /** Up to [limit] rows matching [filter] in the filter's sort order — for batch jobs (rule application), not screens. */
    suspend fun list(filter: TransactionFilter, limit: Int): List<Transaction>

    /** Full-text search (NFR-3); [query] is free text — the repository tokenises and escapes it (NFR-20c). */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<Transaction>

    suspend fun transactions(accountId: AccountId, range: DateRange): List<Transaction>

    /** Inserts or updates the transaction with its splits and tags atomically. */
    suspend fun save(transaction: Transaction)

    suspend fun saveAll(transactions: List<Transaction>)

    suspend fun delete(id: TransactionId)

    suspend fun restore(id: TransactionId)

    /** Soft-deletes every id in one transaction (bulk delete, FR-TXN-9); returns how many rows changed. */
    suspend fun deleteAll(ids: Set<TransactionId>): Int

    suspend fun restoreAll(ids: Set<TransactionId>)

    suspend fun setExcluded(id: TransactionId, excluded: Boolean)

    suspend fun setCategory(id: TransactionId, categoryId: CategoryId?, source: CategorySource)

    /** Applies [transform] to every live row in [ids] inside one write transaction (bulk recategorize/tag/exclude). */
    suspend fun updateAll(ids: Set<TransactionId>, transform: (Transaction) -> Transaction): Int

    /**
     * Links two rows on different accounts with opposite signs as a transfer pair (FR-TXN-9 "mark as transfer"):
     * both become `TRANSFER` in the transfer category and point at each other.
     */
    suspend fun markTransfer(first: TransactionId, second: TransactionId): Outcome<Unit>


    /** Possible duplicates of [candidate] on the same account: same amount within ±1 day (FR-TXN-11, FR-IMP-5). */
    suspend fun findDuplicates(candidate: Transaction): List<Transaction>

    fun observeCount(): Flow<Int>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 50
    }
}

/** Read-only aggregates over transactions (doc 02 §5.3) for headers, budgets, reports and the add sheet. */
interface SpendingRepository {
    /** Counted expenses per category in the home currency (transfers and excluded rows never count — FR-TXN-10). */
    fun observeSpendByCategory(range: DateRange): Flow<List<CategorySpend>>

    fun observeSpendTotal(range: DateRange): Flow<Money>

    /** Counted expense per day within [range], oldest first. */
    suspend fun dailySpend(range: DateRange): List<DayTotal>

    /**
     * Per-day totals of the rows [filter] would list, for day headers (FR-TXN-3): expenses and income that count,
     * transfers and excluded rows left out. Newest first.
     */
    fun observeDayTotals(filter: TransactionFilter): Flow<List<DayTotal>>

    /** Most-used categories for [type] since [since], most frequent first — the "likely" chips of the add sheet. */
    suspend fun frequentCategories(type: TransactionType, since: LocalDate, limit: Int): List<CategoryId>

    /** Counted expense inside a budget's [scope] as a positive magnitude; re-emits as rows change (FR-BUD-3). */
    fun observeSpend(scope: SpendScope): Flow<Money>

    suspend fun spend(scope: SpendScope): Money

    /** Positive per-day totals inside [scope], oldest first — the cumulative curve of a budget period. */
    suspend fun dailySpend(scope: SpendScope): List<DayTotal>

    /** Positive per-category totals inside [scope], largest first — the breakdown on the budget detail. */
    suspend fun spendByCategory(scope: SpendScope): List<CategorySpend>
}

interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    suspend fun budgets(): List<Budget>

    fun observeBudget(id: BudgetId): Flow<Budget?>

    suspend fun save(budget: Budget)

    suspend fun delete(id: BudgetId)

    suspend fun snooze(id: BudgetId, untilPeriodKey: String?)

    /** Records that [threshold] fired for [periodKey]; returns false when it had already been recorded (FR-BUD-4). */
    suspend fun markAlerted(id: BudgetId, periodKey: String, threshold: Int): Boolean

    suspend fun alertedThresholds(id: BudgetId, periodKey: String): Set<Int>
}

/** Debug-build helpers surfaced from Settings; no-ops in release. */
/**
 * Whether developer tools (sample data, catalogue) may run: debuggable builds, plus the `benchmark` build type that
 * needs seeded data without being debuggable (NFR-2). Release builds always answer `false`.
 */
interface DevelopmentGate {
    val toolsEnabled: Boolean
}

interface DevelopmentRepository {
    val isDebugBuild: Boolean

    suspend fun seedSampleData(months: Int = DEFAULT_MONTHS): Outcome<Int>

    companion object {
        const val DEFAULT_MONTHS = 18
    }
}
