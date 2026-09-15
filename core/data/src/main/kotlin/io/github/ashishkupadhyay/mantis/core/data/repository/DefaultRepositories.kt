package io.github.ashishkupadhyay.mantis.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.data.mapper.toEntity
import io.github.ashishkupadhyay.mantis.core.data.mapper.toModel
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetAlertLogEntity
import io.github.ashishkupadhyay.mantis.core.database.seed.DefaultCategorySeeder
import io.github.ashishkupadhyay.mantis.core.datastore.SyncStateDataSource
import io.github.ashishkupadhyay.mantis.core.datastore.UserPreferencesDataSource
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.BudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
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
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.CategorySpend
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.DayTotal
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.DeviceId
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.SpendScope
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Room-backed repositories. Every write stamps the sync columns (`updatedAt`, `dirty`) with this device's id and
 * appends an outbox entry inside the same transaction, so a crash between the two is impossible (doc 03 §6).
 */

@Singleton
class DefaultPreferencesRepository @Inject constructor(private val source: UserPreferencesDataSource) : PreferencesRepository {
    override val preferences: Flow<UserPreferences> = source.data

    override suspend fun update(transform: (UserPreferences) -> UserPreferences): UserPreferences = source.update(transform)
}

/** Stamps [SyncMeta] for a local change: new HLC value, dirty, this device (doc 02 §5.1). */
class SyncStamper @Inject constructor(private val clock: Clock, private val syncState: SyncStateDataSource) {
    suspend fun stamp(meta: SyncMeta): SyncMeta =
        meta.copy(updatedAt = clock.epochMillis(), dirty = true, deviceId = DeviceId(syncState.deviceId()))

    suspend fun fresh(): SyncMeta = SyncMeta.local(clock.epochMillis(), DeviceId(syncState.deviceId()))

    fun now(): Long = clock.epochMillis()
}

@Singleton
class DefaultAccountRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
) : AccountRepository {

    private val accounts get() = db.accountDao()

    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> =
        accounts.observeAll().map { rows -> rows.map { it.toModel() }.filter { includeArchived || !it.isArchived } }

    override fun observeAccount(id: AccountId): Flow<Account?> = accounts.observeById(id.value).map { it?.toModel() }

    override suspend fun getAccount(id: AccountId): Account? = accounts.getById(id.value)?.toModel()

    override fun observeBalance(id: AccountId): Flow<Money> =
        combine(accounts.observeById(id.value), db.transactionDao().observeBalanceDelta(id.value)) { account, delta ->
            val currency = account?.currency?.let(Currency::of) ?: Currency.INR
            Money((account?.openingBalanceMinor ?: 0L) + delta, currency)
        }

    override fun observeBalances(includeArchived: Boolean): Flow<List<AccountBalance>> =
        combine(observeAccounts(includeArchived), db.transactionDao().observeBalanceDeltas()) { rows, deltas ->
            val byAccount = deltas.associate { it.accountId to it.deltaMinor }
            rows.map { account ->
                AccountBalance(account, account.openingBalance + Money(byAccount[account.id.value] ?: 0L, account.currency))
            }
        }

    override suspend fun save(account: Account) {
        val stamped = account.copy(meta = stamper.stamp(account.meta))
        db.write {
            accounts.upsert(stamped.toEntity())
            outbox.upserted(OutboxWriter.ACCOUNTS, stamped.id.value)
        }
    }

    override suspend fun setArchived(id: AccountId, archived: Boolean) = db.write {
        accounts.setArchived(id.value, archived, stamper.now())
        outbox.upserted(OutboxWriter.ACCOUNTS, id.value)
    }

    override suspend fun delete(id: AccountId): Outcome<Int> {
        val existing = accounts.getById(id.value)
        if (existing == null || existing.sync.deletedAt != null) return Outcome.failure(AppError.NotFound("Account", id.value))
        val removed = db.write {
            val at = stamper.now()
            val rows = db.transactionDao().softDeleteByAccount(id.value, at)
            accounts.softDelete(id.value, at)
            outbox.deleted(OutboxWriter.ACCOUNTS, id.value)
            rows
        }
        return Outcome.success(removed)
    }
}

@Singleton
class DefaultCategoryRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
    private val seeder: DefaultCategorySeeder,
    private val syncState: SyncStateDataSource,
) : CategoryRepository {

    private val categories get() = db.categoryDao()

    override fun observeCategories(): Flow<List<Category>> = categories.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun categories(): List<Category> = categories.all().map { it.toModel() }

    override suspend fun getCategory(id: CategoryId): Category? = categories.getById(id.value)?.toModel()

    override suspend fun byKey(key: String): Category? = categories.getByKey(key)?.toModel()

    override suspend fun save(category: Category) {
        val stamped = category.copy(meta = stamper.stamp(category.meta))
        db.write {
            categories.upsert(stamped.toEntity())
            outbox.upserted(OutboxWriter.CATEGORIES, stamped.id.value)
        }
    }

    override suspend fun saveAll(categories: List<Category>) {
        if (categories.isEmpty()) return
        db.write {
            categories.forEach { category ->
                val stamped = category.copy(meta = stamper.stamp(category.meta))
                this.categories.upsert(stamped.toEntity())
                outbox.upserted(OutboxWriter.CATEGORIES, stamped.id.value)
            }
        }
    }

    override suspend fun setHidden(id: CategoryId, hidden: Boolean) = db.write {
        categories.setHidden(id.value, hidden, stamper.now())
        outbox.upserted(OutboxWriter.CATEGORIES, id.value)
    }

    override suspend fun delete(id: CategoryId): Outcome<Unit> {
        val deleted = db.write {
            categories.softDeleteUserCategory(id.value, stamper.now())
                .also { if (it > 0) outbox.deleted(OutboxWriter.CATEGORIES, id.value) }
        }
        return if (deleted > 0) Outcome.success(Unit) else Outcome.failure(AppError.Validation("id", "Only user categories can be deleted"))
    }

    override suspend fun ensureDefaults() {
        seeder.seed(syncState.deviceId())
    }

    override fun observeRules(): Flow<List<CategoryRule>> = categories.observeRules().map { rows -> rows.map { it.toModel() } }

    override suspend fun rules(): List<CategoryRule> = categories.rules().map { it.toModel() }

    override suspend fun saveRule(rule: CategoryRule) {
        val stamped = rule.copy(meta = stamper.stamp(rule.meta))
        db.write {
            categories.upsertRule(stamped.toEntity())
            outbox.upserted(OutboxWriter.CATEGORY_RULES, stamped.id.value)
        }
    }

    override suspend fun deleteRule(id: RuleId) = db.write {
        if (categories.softDeleteRule(id.value, stamper.now()) > 0) outbox.deleted(OutboxWriter.CATEGORY_RULES, id.value)
    }
}

@Singleton
class DefaultTagRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
    private val ids: UuidV7,
) : TagRepository {

    private val tags get() = db.tagDao()

    override fun observeTags(): Flow<List<Tag>> = tags.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun tags(ids: Set<TagId>): List<Tag> =
        if (ids.isEmpty()) emptyList() else tags.byIds(ids.map { it.value }).map { it.toModel() }

    override suspend fun findOrCreate(name: String): Tag {
        tags.byNormalizedName(Tag.normalize(name))?.takeIf { it.sync.deletedAt == null }?.let { return it.toModel() }
        val tag = Tag(TagId(ids.nextString()), name.trim().removePrefix("#"), meta = stamper.fresh())
        save(tag)
        return tag
    }

    override suspend fun save(tag: Tag) {
        val stamped = tag.copy(meta = stamper.stamp(tag.meta))
        db.write {
            tags.upsert(stamped.toEntity())
            outbox.upserted(OutboxWriter.TAGS, stamped.id.value)
        }
    }

    override suspend fun delete(id: TagId) = db.write {
        if (tags.softDelete(id.value, stamper.now()) > 0) outbox.deleted(OutboxWriter.TAGS, id.value)
    }
}

@Singleton
class DefaultTransactionRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
    private val writer: TransactionWriter,
) : TransactionRepository {

    private val transactions get() = db.transactionDao()

    /** Tombstones read as "gone": a deleted row must not reopen in a detail screen that is still observing it. */
    override fun observeTransaction(id: TransactionId): Flow<Transaction?> =
        transactions.observeWithRelations(id.value).map { it?.toModel()?.takeUnless { t -> t.meta.isDeleted } }

    override suspend fun getTransaction(id: TransactionId): Transaction? =
        transactions.withRelations(listOf(id.value)).firstOrNull()?.toModel()?.takeUnless { it.meta.isDeleted }

    override fun pagedTransactions(filter: TransactionFilter): Flow<PagingData<Transaction>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false)) {
            if (filter.isEmpty) transactions.pagedAll() else transactions.pagedRaw(TransactionQueryBuilder.build(filter))
        }.flow.map { page -> page.map { it.toModel() } }

    override suspend fun list(filter: TransactionFilter, limit: Int): List<Transaction> =
        transactions.listRaw(TransactionQueryBuilder.build(filter, limit)).map { it.toModel() }

    override suspend fun search(query: String, limit: Int): List<Transaction> {
        val match = TransactionQueryBuilder.ftsMatchExpression(query) ?: return emptyList()
        val rows = transactions.search(match, limit)
        return transactions.withRelations(rows.map { it.id }).map { it.toModel() }.sortedByDescending { it.postedAt }
    }

    override suspend fun transactions(accountId: AccountId, range: DateRange): List<Transaction> {
        val rows = transactions.inRange(accountId.value, range.start.toString(), range.endInclusive.toString())
        return rows.map { it.toModel() }
    }

    override suspend fun save(transaction: Transaction) = saveAll(listOf(transaction))

    override suspend fun saveAll(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        db.write { writer.write(transactions) }
    }

    override suspend fun delete(id: TransactionId) = db.write {
        if (transactions.softDelete(id.value, stamper.now()) > 0) outbox.deleted(OutboxWriter.TRANSACTIONS, id.value)
    }

    override suspend fun restore(id: TransactionId) = db.write {
        if (transactions.restore(id.value, stamper.now()) > 0) outbox.upserted(OutboxWriter.TRANSACTIONS, id.value)
    }

    override suspend fun deleteAll(ids: Set<TransactionId>): Int {
        if (ids.isEmpty()) return 0
        return db.write {
            val at = stamper.now()
            ids.sumOf { id ->
                transactions.softDelete(id.value, at).also { if (it > 0) outbox.deleted(OutboxWriter.TRANSACTIONS, id.value) }
            }
        }
    }

    override suspend fun restoreAll(ids: Set<TransactionId>) {
        if (ids.isEmpty()) return
        db.write {
            val at = stamper.now()
            ids.forEach { id ->
                if (transactions.restore(id.value, at) > 0) outbox.upserted(OutboxWriter.TRANSACTIONS, id.value)
            }
        }
    }

    override suspend fun updateAll(ids: Set<TransactionId>, transform: (Transaction) -> Transaction): Int {
        if (ids.isEmpty()) return 0
        val live = transactions.withRelations(ids.map { it.value }).map { it.toModel() }.filter { !it.meta.isDeleted }
        saveAll(live.map(transform))
        return live.size
    }

    override suspend fun markTransfer(first: TransactionId, second: TransactionId): Outcome<Unit> {
        val rows = transactions.withRelations(listOf(first.value, second.value)).map { it.toModel() }.filter { !it.meta.isDeleted }
        val a = rows.firstOrNull { it.id == first } ?: return Outcome.failure(AppError.NotFound("Transaction", first.value))
        val b = rows.firstOrNull { it.id == second } ?: return Outcome.failure(AppError.NotFound("Transaction", second.value))
        transferPairError(a, b)?.let { return Outcome.failure(it) }
        val transfer = db.categoryDao().getByKey(DefaultTaxonomy.KEY_TRANSFER)?.id?.let(::CategoryId)
        fun Transaction.asLeg(pair: Transaction) = copy(
            type = TransactionType.TRANSFER,
            categoryId = transfer ?: categoryId,
            categorySource = CategorySource.USER,
            categoryConfidence = null,
            needsReview = false,
            transferPairId = pair.id,
        )
        saveAll(listOf(a.asLeg(b), b.asLeg(a)))
        return Outcome.success(Unit)
    }

    private fun transferPairError(a: Transaction, b: Transaction): AppError? = when {
        a.accountId == b.accountId -> AppError.Validation("account", "Pick rows on two different accounts")
        (a.amount.minor < 0) == (b.amount.minor < 0) -> AppError.Validation("amount", "One row must be money out and the other money in")
        a.amount.abs() != b.amount.abs() -> AppError.Validation("amount", "The two amounts must match")
        else -> null
    }

    override suspend fun setExcluded(id: TransactionId, excluded: Boolean) {
        updateAll(setOf(id)) { it.copy(isExcluded = excluded) }
    }

    override suspend fun setCategory(id: TransactionId, categoryId: CategoryId?, source: CategorySource) {
        updateAll(setOf(id)) { it.copy(categoryId = categoryId, categorySource = source, needsReview = false, categoryConfidence = null) }
    }

    override suspend fun findDuplicates(candidate: Transaction): List<Transaction> {
        val from = candidate.postedLocalDate.minusDays(1).toString()
        val to = candidate.postedLocalDate.plusDays(1).toString()
        return transactions.inRange(candidate.accountId.value, from, to)
            .filter { it.id != candidate.id.value && it.amountMinor == candidate.amount.minor }
            .map { it.toModel() }
    }

    override fun observeCount(): Flow<Int> = transactions.observeCount()

    private companion object {
        const val PAGE_SIZE = 50
    }
}

@Singleton
class DefaultSpendingRepository @Inject constructor(
    private val db: MantisDatabase,
    private val preferences: UserPreferencesDataSource,
) : SpendingRepository {

    private val transactions get() = db.transactionDao()

    /** Aggregates are reported in the home currency (v1 is single-currency per user; doc 01 FR-SET-2). */
    private val homeCurrency: Flow<Currency> = preferences.data.map { Currency.of(it.currencyCode) }

    override fun observeSpendByCategory(range: DateRange): Flow<List<CategorySpend>> =
        combine(
            transactions.observeSpendByCategory(range.start.toString(), range.endInclusive.toString()),
            homeCurrency,
        ) { rows, currency ->
            rows.map { CategorySpend(it.categoryId?.let(::CategoryId), Money(it.totalMinor, currency), it.count) }
        }

    override fun observeSpendTotal(range: DateRange): Flow<Money> =
        combine(transactions.observeSpendTotal(range.start.toString(), range.endInclusive.toString()), homeCurrency) { total, currency ->
            Money(total, currency)
        }

    override suspend fun dailySpend(range: DateRange): List<DayTotal> {
        val currency = homeCurrency.first()
        return transactions.dailySpend(range.start.toString(), range.endInclusive.toString())
            .map { DayTotal(LocalDate.parse(it.day), Money(it.totalMinor, currency), it.count) }
    }

    override fun observeDayTotals(filter: TransactionFilter): Flow<List<DayTotal>> =
        combine(transactions.observeDayTotalsRaw(TransactionQueryBuilder.buildDayTotals(filter)), homeCurrency) { rows, currency ->
            rows.map { DayTotal(LocalDate.parse(it.day), Money(it.totalMinor, currency), it.count) }
        }

    override suspend fun frequentCategories(type: TransactionType, since: LocalDate, limit: Int): List<CategoryId> =
        transactions.frequentCategories(type, since.toString(), limit).map(::CategoryId)

    // Expense rows are stored negative; budgets speak in positive magnitudes.
    override fun observeSpend(scope: SpendScope): Flow<Money> = combine(
        scope.run {
            transactions.observeScopedSpend(
                range.start.toString(), range.endInclusive.toString(),
                categoryIds.isEmpty(), categoryIds.map { it.value }, accountIds.isEmpty(), accountIds.map { it.value },
            )
        },
        homeCurrency,
    ) { total, currency -> Money(-total, currency) }

    override suspend fun spend(scope: SpendScope): Money = observeSpend(scope).first()

    override suspend fun dailySpend(scope: SpendScope): List<DayTotal> {
        val currency = homeCurrency.first()
        return scope.run {
            transactions.scopedDailySpend(
                range.start.toString(), range.endInclusive.toString(),
                categoryIds.isEmpty(), categoryIds.map { it.value }, accountIds.isEmpty(), accountIds.map { it.value },
            )
        }.map { DayTotal(LocalDate.parse(it.day), Money(-it.totalMinor, currency), it.count) }
    }

    override suspend fun spendByCategory(scope: SpendScope): List<CategorySpend> {
        val currency = homeCurrency.first()
        return scope.run {
            transactions.scopedSpendByCategory(
                range.start.toString(), range.endInclusive.toString(),
                categoryIds.isEmpty(), categoryIds.map { it.value }, accountIds.isEmpty(), accountIds.map { it.value },
            )
        }.map { CategorySpend(it.categoryId?.let(::CategoryId), Money(-it.totalMinor, currency), it.count) }
    }
}

@Singleton
class DefaultBudgetRepository @Inject constructor(
    private val db: MantisDatabase,
    private val stamper: SyncStamper,
    private val outbox: OutboxWriter,
) : BudgetRepository {

    private val budgets get() = db.budgetDao()

    override fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun budgets(): List<Budget> = budgets.all().map { it.toModel() }

    override fun observeBudget(id: BudgetId): Flow<Budget?> = budgets.observeById(id.value).map { it?.toModel() }

    override suspend fun save(budget: Budget) {
        val stamped = budget.copy(meta = stamper.stamp(budget.meta))
        db.write {
            budgets.replace(stamped.toEntity(), stamped.categoryIds.map { it.value }.toSet(), stamped.accountIds.map { it.value }.toSet())
            outbox.upserted(OutboxWriter.BUDGETS, stamped.id.value)
        }
    }

    override suspend fun delete(id: BudgetId) = db.write {
        if (budgets.softDelete(id.value, stamper.now()) > 0) outbox.deleted(OutboxWriter.BUDGETS, id.value)
    }

    override suspend fun snooze(id: BudgetId, untilPeriodKey: String?) = db.write {
        budgets.snooze(id.value, untilPeriodKey, stamper.now())
        outbox.upserted(OutboxWriter.BUDGETS, id.value)
    }

    override suspend fun markAlerted(id: BudgetId, periodKey: String, threshold: Int): Boolean =
        budgets.logAlert(BudgetAlertLogEntity(id.value, periodKey, threshold, stamper.now())) != -1L

    override suspend fun alertedThresholds(id: BudgetId, periodKey: String): Set<Int> =
        budgets.loggedThresholds(id.value, periodKey).toSet()

    private fun io.github.ashishkupadhyay.mantis.core.database.dao.BudgetWithScope.toModel(): Budget =
        budget.toModel(categories.map { CategoryId(it.categoryId) }.toSet(), accounts.map { AccountId(it.accountId) }.toSet())
}

/** One immediate write transaction on the writer connection (Room 3 driver API). */
internal suspend fun <T> MantisDatabase.write(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
