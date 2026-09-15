package io.github.ashishkupadhyay.mantis.core.data.repository

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.database.seed.DefaultCategorySeeder
import io.github.ashishkupadhyay.mantis.core.datastore.SyncStateDataSource
import io.github.ashishkupadhyay.mantis.core.datastore.UserPreferencesDataSource
import io.github.ashishkupadhyay.mantis.core.datastore.model.SyncState
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.DeviceId
import io.github.ashishkupadhyay.mantis.core.model.ImportBatch
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.Split
import io.github.ashishkupadhyay.mantis.core.model.SplitId
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionFingerprint
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.testing.datastore.InMemoryDataStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Repository semantics over a real in-memory Room DB: stamping, outbox, filters, FTS, aggregates, scopes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("MaxLineLength")
class RepositoriesTest {

    private lateinit var db: MantisDatabase
    private val clock = FixedClock(Instant.parse("2026-09-13T10:00:00Z"))
    private val ids = UuidV7(nowMillis = { clock.epochMillis() })
    private val syncState = SyncStateDataSource(InMemoryDataStore(SyncState(deviceId = "dev-1")), ids)
    private val prefs = UserPreferencesDataSource(InMemoryDataStore(UserPreferences()))
    private lateinit var stamper: SyncStamper
    private lateinit var outbox: OutboxWriter
    private lateinit var accounts: DefaultAccountRepository
    private lateinit var categories: DefaultCategoryRepository
    private lateinit var tags: DefaultTagRepository
    private lateinit var transactions: DefaultTransactionRepository
    private lateinit var spending: DefaultSpendingRepository
    private lateinit var imports: DefaultImportRepository
    private lateinit var budgets: DefaultBudgetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<MantisDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(AndroidSQLiteDriver())
            .build()
        stamper = SyncStamper(clock, syncState)
        outbox = OutboxWriter(db.outboxDao(), clock)
        accounts = DefaultAccountRepository(db, stamper, outbox)
        categories = DefaultCategoryRepository(db, stamper, outbox, DefaultCategorySeeder(db, clock, ids), syncState)
        tags = DefaultTagRepository(db, stamper, outbox, ids)
        val writer = TransactionWriter(db, stamper, outbox, clock)
        transactions = DefaultTransactionRepository(db, stamper, outbox, writer)
        spending = DefaultSpendingRepository(db, prefs)
        imports = DefaultImportRepository(db, stamper, outbox, writer)
        budgets = DefaultBudgetRepository(db, stamper, outbox)
    }

    @After
    fun tearDown() = db.close()

    private val meta = SyncMeta.local(0L, DeviceId("stale"))

    private fun account(name: String = "HDFC") = Account(
        id = AccountId(ids.nextString()), name = name, type = AccountType.BANK, currency = Currency.INR,
        openingBalance = Money.ofMajor(1000, Currency.INR), openingDate = LocalDate.of(2026, 1, 1), meta = meta,
    )

    private fun expense(account: Account, minor: Long, description: String, date: LocalDate, category: CategoryId? = null) = Transaction(
        id = TransactionId(ids.nextString()), accountId = account.id, type = TransactionType.EXPENSE, amount = Money(-minor, Currency.INR),
        postedAt = date.atStartOfDay(clock.zone).toInstant(), postedLocalDate = date, descriptionRaw = description,
        categoryId = category, fingerprint = "fp-$description-$minor", meta = meta,
    )

    @Test
    fun savesStampSyncColumnsWithThisDeviceAndWriteTheOutbox() = runTest {
        val account = account()

        accounts.save(account)

        val stored = accounts.getAccount(account.id).shouldNotBeNull()
        stored.meta.deviceId shouldBe DeviceId("dev-1")
        stored.meta.dirty shouldBe true
        stored.meta.updatedAt shouldBe clock.epochMillis()
        val queued = db.outboxDao().next(10)
        queued shouldHaveSize 1
        queued.single().entityId shouldBe account.id.value
    }

    @Test
    fun FR_ACC_2_balanceIsOpeningPlusLivePostedRows() = runTest {
        val account = account()
        accounts.save(account)
        transactions.saveAll(
            listOf(
                expense(account, 250_00, "COFFEE", LocalDate.of(2026, 9, 1)),
                expense(account, 100_00, "AUTO", LocalDate.of(2026, 9, 2)),
            ),
        )

        accounts.observeBalance(account.id).first() shouldBe Money.ofMajor(650, Currency.INR)

        transactions.delete(transactions.search("AUTO").single().id)
        accounts.observeBalance(account.id).first() shouldBe Money.ofMajor(750, Currency.INR)
    }

    @Test
    fun FR_ACC_4_deleteSoftDeletesTheAccountAndItsRowsAndBalancesListEveryAccount() = runTest {
        val account = account()
        val other = account("ICICI")
        accounts.save(account)
        accounts.save(other)
        transactions.save(expense(account, 1_00, "X", LocalDate.of(2026, 9, 1)))
        transactions.save(expense(other, 5_00, "Y", LocalDate.of(2026, 9, 1)))

        accounts.observeBalances().first().map { it.balance.minor } shouldContainExactly listOf(999_00L, 995_00L)

        val removed = accounts.delete(account.id).shouldBeInstanceOf<Outcome.Success<Int>>()
        removed.value shouldBe 1
        accounts.observeAccounts().first().map { it.id } shouldContainExactly listOf(other.id)
        transactions.getTransaction(transactions.search("X").firstOrNull()?.id ?: TransactionId("none")) shouldBe null
        accounts.delete(account.id).shouldBeInstanceOf<Outcome.Failure>()
    }

    @Test
    fun FR_ONB_8_ensureDefaultsSeedsOnceAndSystemCategoriesCannotBeDeleted() = runTest {
        categories.ensureDefaults()
        categories.ensureDefaults()

        val all = categories.categories()
        all shouldHaveSize DefaultTaxonomy.groups.size + DefaultTaxonomy.leaves.size
        val groceries = categories.byKey("food.groceries").shouldNotBeNull()
        categories.delete(groceries.id).shouldBeInstanceOf<Outcome.Failure>()
    }

    @Test
    fun FR_TXN_4_filtersRunAsBoundQueriesAndFtsSearchIsSanitised() = runTest {
        categories.ensureDefaults()
        val food = categories.byKey("food.delivery")!!.id
        val account = account()
        val other = account("ICICI")
        accounts.save(account)
        accounts.save(other)
        val tag = tags.findOrCreate("#Goa Trip")
        transactions.saveAll(
            listOf(
                expense(account, 300_00, "SWIGGY ORDER", LocalDate.of(2026, 9, 1), food),
                expense(account, 900_00, "AMAZON.IN", LocalDate.of(2026, 9, 3)).copy(tags = setOf(tag.id)),
                expense(other, 50_00, "SWIGGY INSTAMART", LocalDate.of(2026, 8, 1), food),
            ),
        )

        transactions.pagedTransactions(TransactionFilter(accountIds = setOf(account.id))).asSnapshot().map { it.descriptionRaw } shouldBe
            listOf("AMAZON.IN", "SWIGGY ORDER")
        transactions.pagedTransactions(TransactionFilter(categoryIds = setOf(food))).asSnapshot() shouldHaveSize 2
        transactions.pagedTransactions(TransactionFilter(tagIds = setOf(tag.id))).asSnapshot().single().tags shouldBe setOf(tag.id)
        transactions.pagedTransactions(TransactionFilter(dateRange = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))))
            .asSnapshot() shouldHaveSize 2
        transactions.pagedTransactions(TransactionFilter(query = "swig")).asSnapshot() shouldHaveSize 2
        // Injection attempts degrade to harmless prefix terms (matching nothing) or to no search at all (NFR-20c).
        transactions.pagedTransactions(TransactionFilter(query = "\"; DROP TABLE transactions; --")).asSnapshot().shouldBeEmpty()
        transactions.search("') OR 1=1 --").shouldBeEmpty()
        transactions.pagedTransactions(TransactionFilter.ALL).asSnapshot() shouldHaveSize 3
        TransactionQueryBuilder.ftsMatchExpression("  ") shouldBe null
        TransactionQueryBuilder.ftsMatchExpression("swiggy \"order\" ") shouldBe "swiggy* order*"
    }

    @Test
    fun FR_TXN_6_splitsAndTagsRoundTripAndDuplicatesAreDetectedWithinADay() = runTest {
        categories.ensureDefaults()
        val groceries = categories.byKey("food.groceries")!!.id
        val pharmacy = categories.byKey("health.pharmacy")!!.id
        val account = account()
        accounts.save(account)
        val bill = expense(account, 1000_00, "DMART", LocalDate.of(2026, 9, 5)).copy(
            splits = listOf(
                Split(SplitId(ids.nextString()), groceries, Money(-700_00, Currency.INR)),
                Split(SplitId(ids.nextString()), pharmacy, Money(-300_00, Currency.INR), note = "meds"),
            ),
        )
        transactions.save(bill)

        val loaded = transactions.getTransaction(bill.id).shouldNotBeNull()
        loaded.splits.map { it.amount.minor } shouldContainExactly listOf(-700_00L, -300_00L)

        val twin = expense(account, 1000_00, "DMART AGAIN", LocalDate.of(2026, 9, 6))
        transactions.findDuplicates(twin).map { it.id } shouldContainExactly listOf(bill.id)
        transactions.findDuplicates(twin.copy(postedLocalDate = LocalDate.of(2026, 9, 8))).shouldBeEmpty()
    }

    @Test
    fun spendAggregatesUseTheHomeCurrencyAndSkipExcludedRows() = runTest {
        categories.ensureDefaults()
        val food = categories.byKey("food.delivery")!!.id
        val account = account()
        accounts.save(account)
        val a = expense(account, 200_00, "A", LocalDate.of(2026, 9, 1), food)
        val b = expense(account, 300_00, "B", LocalDate.of(2026, 9, 2), food)
        transactions.saveAll(listOf(a, b))
        prefs.update { it.copy(currencyCode = "USD") }
        val september = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

        spending.observeSpendTotal(september).first() shouldBe Money(-500_00, Currency.USD)
        transactions.setExcluded(b.id, true)
        spending.observeSpendTotal(september).first() shouldBe Money(-200_00, Currency.USD)
        spending.observeSpendByCategory(september).first().single().count shouldBe 1
        transactions.setCategory(a.id, null, CategorySource.USER)
        transactions.getTransaction(a.id)!!.categorySource shouldBe CategorySource.USER
    }

    @Test
    fun FR_TXN_3_dayTotalsFollowTheListFilterAndSkipTransfersAndExcludedRows() = runTest {
        categories.ensureDefaults()
        val food = categories.byKey("food.delivery")!!.id
        val account = account()
        val other = account("ICICI")
        accounts.save(account)
        accounts.save(other)
        val day1 = LocalDate.of(2026, 9, 1)
        val day2 = LocalDate.of(2026, 9, 2)
        val skipped = expense(account, 999_00, "REIMBURSED", day2).copy(isExcluded = true)
        val out = expense(account, 500_00, "TO CARD", day2).copy(type = TransactionType.TRANSFER)
        transactions.saveAll(
            listOf(
                expense(account, 300_00, "SWIGGY", day1, food),
                expense(account, 200_00, "ZOMATO", day1, food),
                expense(other, 50_00, "COFFEE", day2),
                expense(account, 0, "SALARY", day2).copy(type = TransactionType.INCOME, amount = Money(80_000_00, Currency.INR)),
                skipped,
                out,
            ),
        )

        val all = spending.observeDayTotals(TransactionFilter.ALL).first()
        all.map { it.day } shouldContainExactly listOf(day2, day1)
        all[0].total shouldBe Money(80_000_00 - 50_00, Currency.INR)
        all[0].count shouldBe 2
        all[1].total shouldBe Money(-500_00, Currency.INR)
        all[1].count shouldBe 2

        val filtered = spending.observeDayTotals(TransactionFilter(accountIds = setOf(other.id))).first()
        filtered.single().total shouldBe Money(-50_00, Currency.INR)
        spending.observeDayTotals(TransactionFilter(query = "swig")).first().single().count shouldBe 1
        spending.observeDayTotals(TransactionFilter(uncategorizedOnly = true)).first().sumOf { it.count } shouldBe 2
        spending.frequentCategories(TransactionType.EXPENSE, day1, 5) shouldContainExactly listOf(food)
    }

    @Test
    fun FR_TXN_9_bulkEditsAndTransferPairingRunAsOneWrite() = runTest {
        categories.ensureDefaults()
        val food = categories.byKey("food.delivery")!!.id
        val bank = account()
        val card = account("ICICI").copy(type = AccountType.CREDIT_CARD)
        accounts.save(bank)
        accounts.save(card)
        val a = expense(bank, 300_00, "A", LocalDate.of(2026, 9, 1))
        val b = expense(bank, 200_00, "B", LocalDate.of(2026, 9, 1))
        val payment = expense(bank, 18_000_00, "CC PAYMENT", LocalDate.of(2026, 9, 6))
        val received = expense(card, 0, "PAYMENT RECEIVED", LocalDate.of(2026, 9, 6)).copy(amount = Money(18_000_00, Currency.INR), type = TransactionType.INCOME)
        transactions.saveAll(listOf(a, b, payment, received))

        transactions.updateAll(setOf(a.id, b.id)) { it.copy(categoryId = food, categorySource = CategorySource.USER) } shouldBe 2
        transactions.getTransaction(b.id)!!.categoryId shouldBe food

        transactions.markTransfer(payment.id, received.id).shouldBeInstanceOf<Outcome.Success<Unit>>()
        val outLeg = transactions.getTransaction(payment.id).shouldNotBeNull()
        outLeg.type shouldBe TransactionType.TRANSFER
        outLeg.transferPairId shouldBe received.id
        outLeg.categoryId shouldBe categories.byKey(DefaultTaxonomy.KEY_TRANSFER)!!.id
        transactions.getTransaction(received.id)!!.transferPairId shouldBe payment.id
        transactions.markTransfer(a.id, b.id).shouldBeInstanceOf<Outcome.Failure>() // same account, same sign

        transactions.deleteAll(setOf(a.id, b.id, TransactionId("missing"))) shouldBe 2
        transactions.pagedTransactions(TransactionFilter.ALL).asSnapshot() shouldHaveSize 2
        transactions.restoreAll(setOf(a.id, b.id))
        transactions.pagedTransactions(TransactionFilter.ALL).asSnapshot() shouldHaveSize 4
        db.outboxDao().next(100).count { it.entityId == a.id.value } shouldBe 4 // save, bulk update, delete, restore
    }

    @Test
    fun FR_IMP_6_FR_IMP_8_importsDedupeCommitAtomicallyAndUndoByBatch() = runTest {
        val account = account()
        accounts.save(account)
        val existing = expense(account, 349_00, "UPI-SWIGGY-ORDER", LocalDate.of(2026, 9, 1))
            .copy(fingerprint = TransactionFingerprint.of(account.id, LocalDate.of(2026, 9, 1), -349_00, "UPI-SWIGGY-ORDER"))
        transactions.save(existing)
        val rows = listOf(
            ParsedRow(1, emptyList(), LocalDate.of(2026, 9, 1), "UPI-SWIGGY-ORDER", -349_00), // exact fingerprint
            ParsedRow(2, emptyList(), LocalDate.of(2026, 9, 2), "upi-swiggy-order", -349_00), // value-date drift, same text
            ParsedRow(3, emptyList(), LocalDate.of(2026, 9, 2), "AMAZON.IN", -1_250_50),
            ParsedRow(4, emptyList(), error = "bad"),
        )

        val duplicates = imports.findDuplicates(account.id, rows)
        duplicates.keys shouldBe setOf(0, 1)
        duplicates.getValue(0).id shouldBe existing.id

        val batchId = ImportBatchId(ids.nextString())
        val batch = ImportBatch(batchId, account.id, "hdfc.csv", "hdfc", "hash", 4, 1, 2, 0, 1, clock.now())
        val imported = expense(account, 1_250_50, "AMAZON.IN", LocalDate.of(2026, 9, 2)).copy(importBatchId = batchId)
        imports.commit(batch, listOf(imported))

        imports.batch(batchId).shouldNotBeNull().importedCount shouldBe 1
        imports.observeBatches().first().single().id shouldBe batchId
        transactions.pagedTransactions(TransactionFilter.ALL).asSnapshot() shouldHaveSize 2

        imports.undo(batchId) shouldBe 1
        transactions.pagedTransactions(TransactionFilter.ALL).asSnapshot().single().id shouldBe existing.id
        imports.batch(batchId)?.isUndone shouldBe true
        db.outboxDao().next(100).count { it.entityId == imported.id.value } shouldBe 2 // upsert, then delete
    }

    @Test
    fun FR_BUD_1_budgetScopesAndAlertLogRoundTrip() = runTest {
        categories.ensureDefaults()
        val food = categories.byKey("food.delivery")!!.id
        val account = account()
        accounts.save(account)
        val budget = Budget(
            id = BudgetId(ids.nextString()), name = "Eating out", amount = Money.ofMajor(5000, Currency.INR),
            thresholds = listOf(50, 90), categoryIds = setOf(food), accountIds = setOf(account.id), meta = meta,
        )

        budgets.save(budget)

        val loaded = budgets.observeBudget(budget.id).first().shouldNotBeNull()
        loaded.thresholds shouldBe listOf(50, 90)
        loaded.categoryIds shouldBe setOf(food)
        loaded.accountIds shouldBe setOf(account.id)
        budgets.markAlerted(budget.id, "2026-09", 50) shouldBe true
        budgets.markAlerted(budget.id, "2026-09", 50) shouldBe false
        budgets.alertedThresholds(budget.id, "2026-09") shouldBe setOf(50)
    }
}
