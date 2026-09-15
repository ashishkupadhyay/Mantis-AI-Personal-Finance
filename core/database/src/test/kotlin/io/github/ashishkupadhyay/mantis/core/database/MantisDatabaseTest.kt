package io.github.ashishkupadhyay.mantis.core.database

import app.cash.turbine.test
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncColumns
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionSplitEntity
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.SplitOrigin
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Schema v1 behaviour that the repositories will rely on (doc 02 §5.2–5.3), exercised through the seeders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@Suppress("MaxLineLength")
class MantisDatabaseTest {

    private lateinit var db: MantisDatabase

    @Before
    fun setUp() {
        db = TestDatabase.inMemory()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun categorySeederIsIdempotentAndCoversTheTaxonomy() = runTest {
        val seeder = TestDatabase.categorySeeder(db)

        val inserted = seeder.seed("dev-1")
        val again = seeder.seed("dev-1")

        inserted shouldBe DefaultTaxonomy.groups.size + DefaultTaxonomy.leaves.size
        again shouldBe 0
        db.categoryDao().systemCount() shouldBe inserted
        db.categoryDao().getByKey(DefaultTaxonomy.KEY_UNCATEGORIZED).shouldNotBeNull().parentId.shouldNotBeNull()
    }

    @Test
    fun sampleSeederProducesAConsistentHistory() = runTest {
        val result = TestDatabase.sampleSeeder(db).seed(months = 6)

        result.accounts shouldBe 4
        result.transactions shouldBeGreaterThan 300
        db.transactionDao().observeCount().test { awaitItem() shouldBe result.transactions; cancelAndIgnoreRemainingEvents() }

        // Transfer pairs net to zero, so account balances stay consistent (FR-ACC-4).
        val transfers = db.transactionDao().inRange(db.accountDao().active()[0].id, "2000-01-01", "2100-01-01")
            .filter { it.type == TransactionType.TRANSFER }
        transfers.shouldNotBeEmpty()
        transfers.forEach { out -> db.transactionDao().getById(out.transferPairId!!).shouldNotBeNull().amountMinor shouldBe -out.amountMinor }

        // Expenses are money out.
        db.transactionDao().dailySpend("2000-01-01", "2100-01-01").forEach { it.totalMinor shouldBeLessThan 0 }
    }

    @Test
    fun ftsShadowTableIsKeptInSyncByTriggers() = runTest {
        TestDatabase.sampleSeeder(db).seed(months = 3)

        val hits = db.transactionDao().search("swiggy*", limit = 20)

        hits.shouldNotBeEmpty()
        hits.forEach { it.descriptionRaw.contains("SWIGGY") shouldBe true }
        db.transactionDao().search("nosuchmerchantxyz", limit = 20).shouldBeEmpty()
    }

    @Test
    fun spendByCategoryAggregatesOnlyLiveExpenses() = runTest {
        TestDatabase.sampleSeeder(db).seed(months = 3)
        val delivery = db.categoryDao().getByKey("food.delivery")!!.id

        db.transactionDao().observeSpendByCategory("2000-01-01", "2100-01-01").test {
            val rows = awaitItem()
            val row = rows.first { it.categoryId == delivery }
            row.totalMinor shouldBeLessThan 0
            row.count shouldBeGreaterThan 0
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replaceWritesSplitsAndTagsAtomically() = runTest {
        TestDatabase.sampleSeeder(db).seed(months = 2)
        val dao = db.transactionDao()
        val transaction = dao.inRange(db.accountDao().active()[1].id, "2000-01-01", "2100-01-01").first { it.type == TransactionType.EXPENSE }
        val groceries = db.categoryDao().getByKey("food.groceries")!!.id
        val pharmacy = db.categoryDao().getByKey("health.pharmacy")!!.id
        val tag = db.tagDao().observeAll().first().first()
        val half = transaction.amountMinor / 2
        val splits = listOf(
            TransactionSplitEntity(TestDatabase.ids.nextString(), transaction.id, groceries, half, null, SplitOrigin.USER, 0),
            TransactionSplitEntity(TestDatabase.ids.nextString(), transaction.id, pharmacy, transaction.amountMinor - half, "meds", SplitOrigin.USER, 1),
        )

        dao.replace(transaction.copy(notes = "split bill", sync = transaction.sync.copy(dirty = true)), splits, setOf(tag.id))

        dao.observeWithRelations(transaction.id).test {
            val loaded = awaitItem().shouldNotBeNull()
            loaded.transaction.notes shouldBe "split bill"
            loaded.splits shouldHaveSize 2
            loaded.splits.sumOf { it.amountMinor } shouldBe transaction.amountMinor
            loaded.tags.map { it.id } shouldBe listOf(tag.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun softDeleteHidesRowsFromAggregatesUntilRestored() = runTest {
        TestDatabase.sampleSeeder(db).seed(months = 1)
        val dao = db.transactionDao()
        val victim = dao.inRange(db.accountDao().active()[1].id, "2000-01-01", "2100-01-01").first { it.type == TransactionType.EXPENSE }
        val before = dao.observeSpendTotal("2000-01-01", "2100-01-01").first()

        dao.softDelete(victim.id, at = 1L) shouldBe 1
        dao.observeSpendTotal("2000-01-01", "2100-01-01").first() shouldBe before - victim.amountMinor

        dao.restore(victim.id, at = 2L) shouldBe 1
        dao.observeSpendTotal("2000-01-01", "2100-01-01").first() shouldBe before
        dao.getById(victim.id).shouldNotBeNull().sync shouldBe SyncColumns(updatedAt = 2L, version = 0, deletedAt = null, dirty = true, deviceId = "seed-device")
    }
}
