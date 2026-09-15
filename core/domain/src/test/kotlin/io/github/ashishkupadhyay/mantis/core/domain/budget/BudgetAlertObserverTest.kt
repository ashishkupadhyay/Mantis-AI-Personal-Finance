package io.github.ashishkupadhyay.mantis.core.domain.budget

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeBudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeSpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** FR-BUD-4 / FR-NTF-1/5: thresholds fire once per period, offline, immediately after the write. */
class BudgetAlertObserverTest {

    private val inr = Currency.INR
    private val clock = FixedClock(Instant.parse("2026-09-18T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
    private val categories = FakeCategoryRepository.withDefaults()
    private val food = runBlocking { categories.categories() }.first { it.key == "food" }
    private val transactions = FakeTransactionRepository()
    private val budgets = FakeBudgetRepository()
    private val engine = BudgetEngine(FakeSpendingRepository(transactions), categories, FakePreferencesRepository(), clock)
    private val posted = mutableListOf<BudgetAlert>()
    private val observer = BudgetAlertObserver(budgets, engine, { posted += it }, clock)

    // The acceptance scenario is about the 80 % threshold, so the projection alert is off here and tested on its own.
    private val foodBudget = Budget(
        BudgetId("food"), "Food & Dining", Money.ofMajor(8_000, inr), thresholds = listOf(80), alertOnProjectedOverspend = false,
        categoryIds = setOf(food.id), startsOn = LocalDate.of(2026, 1, 1), meta = testMeta(),
    )

    private fun expense(major: Long, day: LocalDate = LocalDate.of(2026, 9, 18), category: CategoryId? = food.id) = Transaction(
        id = TransactionId(newId()), accountId = AccountId("bank"), type = TransactionType.EXPENSE,
        amount = Money.ofMajor(-major, inr), postedAt = Instant.EPOCH, postedLocalDate = day, descriptionRaw = "x",
        categoryId = category, fingerprint = newId(), meta = testMeta(),
    )

    private fun write(vararg rows: Transaction) = runBlocking {
        rows.forEach { transactions.save(it) }
        observer.onWritten(rows.toList())
    }

    @Test
    fun `acceptance a 500 rupee row taking Food and Dining from 6200 to 6700 posts the 84 percent alert once`() = runBlocking<Unit> {
        budgets.save(foodBudget)
        write(expense(6_200, LocalDate.of(2026, 9, 10)))
        posted.shouldBeEmpty() // 77.5 % — under the only threshold

        write(expense(500))
        val alert = posted.single()
        alert.threshold shouldBe 80
        alert.status.percentUsed shouldBe 84
        alert.status.remaining shouldBe Money.ofMajor(1_300, inr)
        alert.status.daysLeft shouldBe 12
        alert.status.budget.name shouldBe "Food & Dining"

        write(expense(100)) // still above 80 %, already announced
        posted.size shouldBe 1
        budgets.alertedThresholds(foodBudget.id, "2026-09") shouldBe setOf(80)
    }

    @Test
    fun `one write that jumps several thresholds announces only the highest, and rows outside the scope stay silent`() = runBlocking<Unit> {
        budgets.save(foodBudget.copy(thresholds = Budget.DEFAULT_THRESHOLDS))
        write(expense(9_000, category = null)) // uncategorized: not food
        posted.shouldBeEmpty()

        write(expense(8_500))
        posted.map { it.threshold } shouldContainExactly listOf(100)
        budgets.alertedThresholds(foodBudget.id, "2026-09") shouldBe setOf(50, 80, 100)
    }

    @Test
    fun `FR_NTF_5 a snoozed budget is quiet for its period and the crossing is announced once the snooze ends`() = runBlocking<Unit> {
        budgets.save(foodBudget.copy(snoozedUntilPeriodKey = "2026-09"))
        write(expense(7_000)) // 87.5 %
        posted.shouldBeEmpty()

        budgets.snooze(foodBudget.id, null)
        write(expense(100))
        posted.map { it.threshold } shouldContainExactly listOf(80)
    }

    @Test
    fun `the projected-overspend alert fires once when the pace says the limit will be passed`() = runBlocking<Unit> {
        budgets.save(foodBudget.copy(thresholds = listOf(100), alertOnProjectedOverspend = true))
        // 18 days in, 5 000 spent → projected 8 333 > 8 000 while the 100 % threshold is still far away.
        write(expense(5_000))
        posted.map { it.threshold } shouldContainExactly listOf(Budget.PROJECTED_ALERT)
        posted.single().isProjected shouldBe true

        write(expense(10))
        posted.size shouldBe 1
    }

    @Test
    fun `early in the period the projection is not trusted for an alert`() = runBlocking<Unit> {
        val early = FixedClock(Instant.parse("2026-09-03T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
        val engine = BudgetEngine(FakeSpendingRepository(transactions), categories, FakePreferencesRepository(), early)
        val observer = BudgetAlertObserver(budgets, engine, { posted += it }, early)
        budgets.save(foodBudget)
        val row = expense(2_000, LocalDate.of(2026, 9, 3))
        transactions.save(row)
        observer.onWritten(listOf(row)) // projected 20 000, but only 3 days in
        posted.shouldBeEmpty()
    }
}
