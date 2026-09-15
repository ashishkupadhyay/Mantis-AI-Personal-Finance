package io.github.ashishkupadhyay.mantis.core.domain.fakes

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.BudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRepository
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.SpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountBalance
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.CategorySpend
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.DayTotal
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.DeviceId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.SpendScope
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * In-memory repositories for ViewModel and use-case tests. They keep the same semantics as `core:data`
 * (soft delete, sorting, uniqueness) without Room, and expose their state so tests can seed and assert.
 */

val TEST_DEVICE: DeviceId = DeviceId("test-device")

fun testMeta(at: Long = 1L): SyncMeta = SyncMeta.local(at, TEST_DEVICE)

fun newId(): String = UUID.randomUUID().toString()

class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    override suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences {
        state.update(transform)
        return state.value
    }
}

class FakeAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
    val accounts = MutableStateFlow(initial.associateBy { it.id })
    val balances = MutableStateFlow<Map<AccountId, Money>>(emptyMap())
    var transactionCount: (AccountId) -> Int = { 0 }

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> = accounts.map { all ->
        all.values.filter { !it.meta.isDeleted && (includeArchived || !it.isArchived) }.sortedWith(compareBy({ it.isArchived }, { it.sortOrder }, { it.name }))
    }

    override fun observeAccount(id: AccountId): Flow<Account?> = accounts.map { live(id) }

    override suspend fun getAccount(id: AccountId): Account? = live(id)

    override fun observeBalance(id: AccountId): Flow<Money> = balances.map { b ->
        b[id] ?: accounts.value[id]?.openingBalance ?: Money.zero(Currency.INR)
    }

    override fun observeBalances(includeArchived: Boolean): Flow<List<AccountBalance>> =
        combine(observeAccounts(includeArchived), balances) { all, b -> all.map { AccountBalance(it, b[it.id] ?: it.openingBalance) } }

    override suspend fun save(account: Account) = accounts.update { it + (account.id to account) }

    override suspend fun setArchived(id: AccountId, archived: Boolean) =
        accounts.update { all -> all[id]?.let { all + (id to it.copy(isArchived = archived)) } ?: all }

    override suspend fun delete(id: AccountId): Outcome<Int> {
        if (live(id) == null) return Outcome.failure(AppError.NotFound("Account", id.value))
        accounts.update { all -> all[id]?.let { all + (id to it.copy(meta = it.meta.copy(deletedAt = 99L))) } ?: all }
        return Outcome.success(transactionCount(id))
    }

    private fun live(id: AccountId): Account? = accounts.value[id]?.takeUnless { it.meta.isDeleted }
}

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val categories = MutableStateFlow(initial.associateBy { it.id })
    val rules = MutableStateFlow<Map<RuleId, CategoryRule>>(emptyMap())

    override fun observeCategories(): Flow<List<Category>> = categories.map { live() }

    override suspend fun categories(): List<Category> = live()

    override suspend fun getCategory(id: CategoryId): Category? = categories.value[id]

    override suspend fun byKey(key: String): Category? = categories.value.values.firstOrNull { it.key == key }

    override suspend fun save(category: Category) = categories.update { it + (category.id to category) }

    override suspend fun saveAll(categories: List<Category>) = categories.forEach { save(it) }

    override suspend fun setHidden(id: CategoryId, hidden: Boolean) =
        categories.update { all -> all[id]?.let { all + (id to it.copy(isHidden = hidden)) } ?: all }

    override suspend fun delete(id: CategoryId): Outcome<Unit> {
        val category = categories.value[id] ?: return Outcome.failure(AppError.NotFound("Category", id.value))
        if (category.isSystem) return Outcome.failure(AppError.Validation("id", "System categories cannot be deleted"))
        categories.update { it - id }
        return Outcome.success(Unit)
    }

    /** Mirrors the real seeder: group and leaf rows from [DefaultTaxonomy], keyed by `key`. */
    override suspend fun ensureDefaults() {
        if (categories.value.values.any { it.isSystem }) return
        val rows = mutableListOf<Category>()
        DefaultTaxonomy.groups.forEachIndexed { groupIndex, group ->
            val groupId = CategoryId(newId())
            rows += Category(groupId, group.name, group.kind, null, group.key, group.icon, isSystem = true, sortOrder = groupIndex, meta = testMeta())
            group.leaves.forEachIndexed { leafIndex, leaf ->
                rows += Category(CategoryId(newId()), leaf.name, group.kind, groupId, leaf.key, leaf.icon, isSystem = true, sortOrder = leafIndex, meta = testMeta())
            }
        }
        categories.update { it + rows.associateBy { row -> row.id } }
    }

    override fun observeRules(): Flow<List<CategoryRule>> = rules.map { it.values.sortedByDescending { r -> r.priority } }

    override suspend fun rules(): List<CategoryRule> = rules.value.values.sortedByDescending { it.priority }

    override suspend fun saveRule(rule: CategoryRule) = rules.update { it + (rule.id to rule) }

    override suspend fun deleteRule(id: RuleId) = rules.update { it - id }

    private fun live(): List<Category> = categories.value.values.filter { !it.meta.isDeleted }
        .sortedWith(compareBy({ it.kind }, { it.sortOrder }, { it.name }))

    companion object {
        fun withDefaults(): FakeCategoryRepository = FakeCategoryRepository().also { repo ->
            kotlinx.coroutines.runBlocking { repo.ensureDefaults() }
        }
    }
}

class FakeTagRepository(initial: List<Tag> = emptyList()) : TagRepository {
    val tags = MutableStateFlow(initial.associateBy { it.id })

    override fun observeTags(): Flow<List<Tag>> = tags.map { all -> all.values.filter { !it.meta.isDeleted }.sortedBy { it.name } }

    override suspend fun tags(ids: Set<TagId>): List<Tag> = ids.mapNotNull { tags.value[it] }

    override suspend fun findOrCreate(name: String): Tag {
        val normalized = Tag.normalize(name)
        tags.value.values.firstOrNull { it.normalizedName == normalized && !it.meta.isDeleted }?.let { return it }
        return Tag(TagId(newId()), name.trim().removePrefix("#"), meta = testMeta()).also { save(it) }
    }

    override suspend fun save(tag: Tag) = tags.update { it + (tag.id to tag) }

    override suspend fun delete(id: TagId) = tags.update { it - id }
}

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val transactions = MutableStateFlow(initial.associateBy { it.id })
    val saved = mutableListOf<Transaction>()

    override fun observeTransaction(id: TransactionId): Flow<Transaction?> = transactions.map { it[id]?.takeUnless { t -> t.meta.isDeleted } }

    override suspend fun getTransaction(id: TransactionId): Transaction? = transactions.value[id]?.takeUnless { it.meta.isDeleted }

    /** Static pages carry settled load states so `asSnapshot()` (paging-testing) returns instead of waiting for a refresh. */
    override fun pagedTransactions(filter: TransactionFilter): Flow<PagingData<Transaction>> =
        transactions.map { PagingData.from(matching(filter), sourceLoadStates = SETTLED) }

    override suspend fun list(filter: TransactionFilter, limit: Int): List<Transaction> = matching(filter).take(limit)

    fun matching(filter: TransactionFilter): List<Transaction> = live().filter { t ->
        val range = filter.dateRange
        (filter.accountIds.isEmpty() || t.accountId in filter.accountIds) &&
            (filter.categoryIds.isEmpty() || t.categoryId in filter.categoryIds) &&
            (filter.tagIds.isEmpty() || t.tags.any { it in filter.tagIds }) &&
            (filter.types.isEmpty() || t.type in filter.types) &&
            (range == null || t.postedLocalDate in range) &&
            (filter.includeExcluded || !t.isExcluded) &&
            (!filter.needsReviewOnly || t.needsReview) &&
            (filter.query.isBlank() || t.descriptionRaw.contains(filter.query, ignoreCase = true))
    }.sortedWith(compareByDescending<Transaction> { it.postedAt }.thenByDescending { it.id.value })

    override suspend fun search(query: String, limit: Int): List<Transaction> =
        live().filter { it.descriptionRaw.contains(query, ignoreCase = true) }.take(limit)

    override suspend fun transactions(accountId: AccountId, range: DateRange): List<Transaction> =
        live().filter { it.accountId == accountId && it.postedLocalDate in range }

    override suspend fun save(transaction: Transaction) {
        saved += transaction
        transactions.update { it + (transaction.id to transaction) }
    }

    override suspend fun saveAll(transactions: List<Transaction>) = transactions.forEach { save(it) }

    override suspend fun delete(id: TransactionId) = mutate(id) { it.copy(meta = it.meta.copy(deletedAt = 99L)) }

    override suspend fun restore(id: TransactionId) = mutate(id) { it.copy(meta = it.meta.copy(deletedAt = null)) }

    override suspend fun deleteAll(ids: Set<TransactionId>): Int {
        val live = ids.filter { transactions.value[it]?.meta?.isDeleted == false }
        live.forEach { delete(it) }
        return live.size
    }

    override suspend fun restoreAll(ids: Set<TransactionId>) = ids.forEach { restore(it) }

    override suspend fun updateAll(ids: Set<TransactionId>, transform: (Transaction) -> Transaction): Int {
        val live = ids.mapNotNull { transactions.value[it] }.filter { !it.meta.isDeleted }
        live.forEach { save(transform(it)) }
        return live.size
    }

    override suspend fun markTransfer(first: TransactionId, second: TransactionId): Outcome<Unit> {
        val a = transactions.value[first] ?: return Outcome.failure(AppError.NotFound("Transaction", first.value))
        val b = transactions.value[second] ?: return Outcome.failure(AppError.NotFound("Transaction", second.value))
        if (a.accountId == b.accountId) return Outcome.failure(AppError.Validation("account", "Pick rows on two different accounts"))
        if ((a.amount.minor < 0) == (b.amount.minor < 0)) return Outcome.failure(AppError.Validation("amount", "Signs must differ"))
        if (a.amount.abs() != b.amount.abs()) return Outcome.failure(AppError.Validation("amount", "The two amounts must match"))
        fun Transaction.asLeg(pair: Transaction) =
            copy(type = TransactionType.TRANSFER, categorySource = CategorySource.USER, needsReview = false, transferPairId = pair.id)
        save(a.asLeg(b))
        save(b.asLeg(a))
        return Outcome.success(Unit)
    }


    override suspend fun setExcluded(id: TransactionId, excluded: Boolean) = mutate(id) { it.copy(isExcluded = excluded) }

    override suspend fun setCategory(id: TransactionId, categoryId: CategoryId?, source: CategorySource) =
        mutate(id) { it.copy(categoryId = categoryId, categorySource = source, needsReview = false) }

    override suspend fun findDuplicates(candidate: Transaction): List<Transaction> = live().filter {
        it.id != candidate.id && it.accountId == candidate.accountId && it.amount == candidate.amount &&
            kotlin.math.abs(it.postedLocalDate.toEpochDay() - candidate.postedLocalDate.toEpochDay()) <= 1
    }

    override fun observeCount(): Flow<Int> = transactions.map { live().size }

    internal fun live() = transactions.value.values.filter { !it.meta.isDeleted }

    private fun mutate(id: TransactionId, transform: (Transaction) -> Transaction) =
        transactions.update { all -> all[id]?.let { all + (id to transform(it)) } ?: all }

    private companion object {
        val SETTLED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}

/** Aggregates over a [FakeTransactionRepository]'s rows, in INR. */
class FakeSpendingRepository(private val source: FakeTransactionRepository) : SpendingRepository {

    override fun observeSpendByCategory(range: DateRange): Flow<List<CategorySpend>> = source.transactions.map {
        spending(range).groupBy { t -> t.categoryId }.map { (category, rows) ->
            CategorySpend(category, Money(rows.sumOf { r -> r.amount.minor }, Currency.INR), rows.size)
        }
    }

    override fun observeSpendTotal(range: DateRange): Flow<Money> =
        source.transactions.map { Money(spending(range).sumOf { t -> t.amount.minor }, Currency.INR) }

    override suspend fun dailySpend(range: DateRange): List<DayTotal> = spending(range)
        .groupBy { it.postedLocalDate }
        .map { (day, rows) -> DayTotal(day, Money(rows.sumOf { it.amount.minor }, Currency.INR), rows.size) }
        .sortedBy { it.day }

    private fun scoped(scope: SpendScope): List<Transaction> = source.live().filter { scope.covers(it) }

    override fun observeSpend(scope: SpendScope): Flow<Money> = source.transactions.map { spend(scope) }

    override suspend fun spend(scope: SpendScope): Money = Money(-scoped(scope).sumOf { it.amount.minor }, Currency.INR)

    override suspend fun dailySpend(scope: SpendScope): List<DayTotal> =
        scoped(scope).groupBy { it.postedLocalDate }.toSortedMap().map { (day, rows) ->
            DayTotal(day, Money(-rows.sumOf { it.amount.minor }, Currency.INR), rows.size)
        }

    override suspend fun spendByCategory(scope: SpendScope): List<CategorySpend> =
        scoped(scope).groupBy { it.categoryId }
            .map { (id, rows) -> CategorySpend(id, Money(-rows.sumOf { it.amount.minor }, Currency.INR), rows.size) }
            .sortedByDescending { it.total.minor }

    override fun observeDayTotals(filter: TransactionFilter): Flow<List<DayTotal>> = source.transactions.map {
        source.matching(filter).filter { t -> t.type != TransactionType.TRANSFER && !t.isExcluded }
            .groupBy { t -> t.postedLocalDate }
            .map { (day, rows) -> DayTotal(day, Money(rows.sumOf { r -> r.amount.minor }, Currency.INR), rows.size) }
            .sortedByDescending { d -> d.day }
    }

    override suspend fun frequentCategories(type: TransactionType, since: LocalDate, limit: Int): List<CategoryId> =
        source.live().filter { it.type == type && it.categoryId != null && !it.postedLocalDate.isBefore(since) }
            .groupingBy { checkNotNull(it.categoryId) }.eachCount().entries.sortedByDescending { it.value }.take(limit).map { it.key }

    private fun spending(range: DateRange) =
        source.live().filter { it.type == TransactionType.EXPENSE && !it.isExcluded && it.postedLocalDate in range }
}

class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    val budgets = MutableStateFlow(initial.associateBy { it.id })
    val alerts = mutableSetOf<Triple<BudgetId, String, Int>>()

    override fun observeBudgets(): Flow<List<Budget>> = budgets.map { live() }

    override suspend fun budgets(): List<Budget> = live()

    override fun observeBudget(id: BudgetId): Flow<Budget?> = budgets.map { it[id] }

    override suspend fun save(budget: Budget) = budgets.update { it + (budget.id to budget) }

    override suspend fun delete(id: BudgetId) = budgets.update { it - id }

    override suspend fun snooze(id: BudgetId, untilPeriodKey: String?) =
        budgets.update { all -> all[id]?.let { all + (id to it.copy(snoozedUntilPeriodKey = untilPeriodKey)) } ?: all }

    override suspend fun markAlerted(id: BudgetId, periodKey: String, threshold: Int): Boolean = alerts.add(Triple(id, periodKey, threshold))

    override suspend fun alertedThresholds(id: BudgetId, periodKey: String): Set<Int> =
        alerts.filter { it.first == id && it.second == periodKey }.map { it.third }.toSet()

    private fun live() = budgets.value.values.filter { !it.meta.isDeleted }.sortedBy { it.name }
}

/** Import batches over a [FakeTransactionRepository]: dedupe by fingerprint / fuzzy, commit, undo by batch. */
class FakeImportRepository(private val source: FakeTransactionRepository) : ImportRepository {
    val batches = MutableStateFlow<Map<ImportBatchId, ImportBatch>>(emptyMap())

    override suspend fun findDuplicates(accountId: AccountId, rows: List<ParsedRow>): Map<Int, Transaction> {
        val existing = source.live().filter { it.accountId == accountId }
        return buildMap {
            rows.forEachIndexed { index, row ->
                if (!row.isValid) return@forEachIndexed
                val date = checkNotNull(row.date)
                val minor = checkNotNull(row.amountMinor)
                val hit = existing.firstOrNull { it.fingerprint == TransactionFingerprint.of(accountId, date, minor, row.description) }
                    ?: existing.firstOrNull {
                        it.amount.minor == minor && it.descriptionRaw.equals(row.description, ignoreCase = true) &&
                            kotlin.math.abs(it.postedLocalDate.toEpochDay() - date.toEpochDay()) <= 1
                    }
                if (hit != null) put(index, hit)
            }
        }
    }

    override suspend fun commit(batch: ImportBatch, transactions: List<Transaction>) {
        batches.update { it + (batch.id to batch) }
        source.saveAll(transactions)
    }

    override suspend fun undo(batchId: ImportBatchId): Int {
        val ids = source.live().filter { it.importBatchId == batchId }.map { it.id }.toSet()
        source.deleteAll(ids)
        batches.update { all -> all[batchId]?.let { all + (batchId to it.copy(undoneAt = Instant.ofEpochMilli(99L))) } ?: all }
        return ids.size
    }

    override fun observeBatches(): Flow<List<ImportBatch>> = batches.map { it.values.sortedByDescending { b -> b.createdAt } }

    override suspend fun batch(id: ImportBatchId): ImportBatch? = batches.value[id]
}

class FakeDevelopmentRepository(override val isDebugBuild: Boolean = true) : DevelopmentRepository {
    var seeded = 0

    override suspend fun seedSampleData(months: Int): Outcome<Int> = Outcome.success(months * 100).also { seeded++ }
}

/** A small expense for tests. */
fun sampleCategoryOfKind(kind: CategoryKind = CategoryKind.EXPENSE): Category =
    Category(CategoryId(newId()), "Sample", kind, key = null, meta = testMeta())
