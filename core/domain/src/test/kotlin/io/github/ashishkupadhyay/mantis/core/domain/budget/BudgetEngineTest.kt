package io.github.ashishkupadhyay.mantis.core.domain.budget

import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakePreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeSpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.newId
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Budget
import io.github.ashishkupadhyay.mantis.core.model.BudgetId
import io.github.ashishkupadhyay.mantis.core.model.BudgetPace
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriodType
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BudgetEngineTest {

    private val inr = Currency.INR
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = FixedClock(Instant.parse("2026-09-18T04:30:00Z"), zone) // 18 Sep 2026, a Friday
    private val categories = FakeCategoryRepository.withDefaults()
    private val all = runBlocking { categories.categories() }
    private val food = all.first { it.key == "food" }
    private val delivery = all.first { it.key == "food.delivery" }
    private val groceries = all.first { it.key == "food.groceries" }
    private val transport = all.first { it.key == "transport.fuel" }
    private val transactions = FakeTransactionRepository()
    private val spending = FakeSpendingRepository(transactions)
    private val preferences = FakePreferencesRepository(UserPreferences(monthStartDay = 5, firstDayOfWeek = 1))
    private val engine = BudgetEngine(spending, categories, preferences, clock)

    private fun budget(
        amountMajor: Long,
        categoryIds: Set<CategoryId> = emptySet(),
        rollover: Boolean = false,
        period: BudgetPeriodType = BudgetPeriodType.MONTHLY,
        startsOn: LocalDate? = LocalDate.of(2026, 1, 1),
        accountIds: Set<AccountId> = emptySet(),
    ) = Budget(
        BudgetId(newId()), "Food", Money.ofMajor(amountMajor, inr), period = period, rollover = rollover,
        categoryIds = categoryIds, accountIds = accountIds, startsOn = startsOn, meta = testMeta(),
    )

    private fun spend(
        day: LocalDate,
        major: Long,
        category: CategoryId? = delivery.id,
        account: String = "bank",
        excluded: Boolean = false,
    ) = runBlocking {
            transactions.save(
                Transaction(
                    id = TransactionId(newId()), accountId = AccountId(account), type = TransactionType.EXPENSE,
                    amount = Money.ofMajor(-major, inr), postedAt = Instant.EPOCH, postedLocalDate = day, descriptionRaw = "x",
                    categoryId = category, isExcluded = excluded, fingerprint = newId(), meta = testMeta(),
                ),
            )
        }

    @Test
    fun `FR_BUD_2 periods align to the month start day, the first weekday and the calendar year`() {
        val periods = BudgetPeriods(monthStartDay = 5, firstDayOfWeek = DayOfWeek.MONDAY)
        val monthly = budget(1)
        periods.current(monthly, LocalDate.of(2026, 9, 18)).let {
            it.start shouldBe LocalDate.of(2026, 9, 5)
            it.endInclusive shouldBe LocalDate.of(2026, 10, 4)
            it.key shouldBe "2026-09"
            it.days shouldBe 30
        }
        periods.current(monthly, LocalDate.of(2026, 9, 3)).key shouldBe "2026-08"
        periods.previous(monthly, periods.current(monthly, LocalDate.of(2026, 9, 18)))?.key shouldBe "2026-08"
        periods.previous(monthly, periods.current(monthly, LocalDate.of(2026, 1, 10)))?.start shouldBe LocalDate.of(2025, 12, 5)

        val weekly = budget(1, period = BudgetPeriodType.WEEKLY)
        periods.current(weekly, LocalDate.of(2026, 9, 18)).let {
            it.start shouldBe LocalDate.of(2026, 9, 14)
            it.endInclusive shouldBe LocalDate.of(2026, 9, 20)
            it.key shouldBe "W2026-09-14"
        }
        BudgetPeriods(1, DayOfWeek.SUNDAY).current(weekly, LocalDate.of(2026, 9, 18)).start shouldBe LocalDate.of(2026, 9, 13)

        val yearly = budget(1, period = BudgetPeriodType.YEARLY)
        periods.current(yearly, LocalDate.of(2026, 9, 18)).let {
            it.key shouldBe "2026"
            it.days shouldBe 365
        }

        val custom = Budget(
            BudgetId("c"), "Goa", Money.ofMajor(1, inr), period = BudgetPeriodType.CUSTOM,
            customStart = LocalDate.of(2026, 12, 20), customEnd = LocalDate.of(2026, 12, 27), meta = testMeta(),
        )
        periods.current(custom, LocalDate.of(2026, 9, 18)).key shouldBe BudgetPeriods.CUSTOM_KEY
        periods.previous(custom, periods.current(custom, LocalDate.of(2026, 9, 18))).shouldBeNull()
    }

    @Test
    fun `FR_BUD_3 status reports spend inside the scope, days left, a projection and the pace`() = runBlocking<Unit> {
        spend(LocalDate.of(2026, 9, 6), 1_000)
        spend(LocalDate.of(2026, 9, 10), 1_500, groceries.id)
        spend(LocalDate.of(2026, 9, 12), 2_000, transport.id) // outside the scope
        spend(LocalDate.of(2026, 9, 2), 4_000) // previous period
        spend(LocalDate.of(2026, 9, 15), 9_999, excluded = true) // never counts

        // A budget on the Food group covers its leaves.
        val status = engine.status(budget(10_000, setOf(food.id)))
        status.period.key shouldBe "2026-09"
        status.spent shouldBe Money.ofMajor(2_500, inr)
        status.limit shouldBe Money.ofMajor(10_000, inr)
        status.remaining shouldBe Money.ofMajor(7_500, inr)
        status.percentUsed shouldBe 25
        status.hasReached(25) shouldBe true
        status.hasReached(26) shouldBe false
        status.elapsedDays shouldBe 14 // 5..18 Sep
        status.daysLeft shouldBe 16 // 19 Sep..4 Oct
        status.projected shouldBe Money(2_500_00L * 30 / 14, inr)
        status.pace shouldBe BudgetPace.ON_TRACK
        status.lowConfidence shouldBe true

        engine.status(budget(10_000)).spent shouldBe Money.ofMajor(4_500, inr) // all spending
        engine.status(budget(10_000, accountIds = setOf(AccountId("other")))).spent shouldBe Money.zero(inr)
        engine.status(budget(3_000, setOf(food.id))).pace shouldBe BudgetPace.OVER // 2 500 × 30 / 14 ≈ 5 357 > 3 300
        engine.status(budget(5_000, setOf(food.id))).pace shouldBe BudgetPace.WATCH // 5 357 ≤ 5 500
        engine.status(budget(2_000, setOf(food.id))).isOver shouldBe true
        engine.observeStatus(budget(10_000, setOf(food.id))).first().spent shouldBe Money.ofMajor(2_500, inr)
    }

    @Test
    fun `rollover carries the previous period's leftover, clamped to twice the amount, from the start date`() = runBlocking<Unit> {
        spend(LocalDate.of(2026, 8, 10), 3_000) // Aug period (5 Aug..4 Sep): 3 000 of 8 000 → carry 5 000
        spend(LocalDate.of(2026, 7, 10), 20_000) // Jul period: overspent by 12 000 → clamps Aug's limit to 0

        // Starts inside the July period (5 Jul..4 Aug), so July is the base period with the plain amount.
        val fromJuly = budget(8_000, setOf(delivery.id), rollover = true, startsOn = LocalDate.of(2026, 7, 10))
        // Aug limit = clamp(8 000 + (8 000 − 20 000)) = 0; Sep limit = clamp(8 000 + (0 − 3 000)) = 5 000.
        engine.status(fromJuly).limit shouldBe Money.ofMajor(5_000, inr)

        val fromAugust = budget(8_000, setOf(delivery.id), rollover = true, startsOn = LocalDate.of(2026, 8, 6))
        // July predates the budget: Aug limit = 8 000 → Sep = 8 000 + 5 000 = 13 000.
        val status = engine.status(fromAugust)
        status.limit shouldBe Money.ofMajor(13_000, inr)
        status.rollover shouldBe Money.ofMajor(5_000, inr)

        // Nothing spent on groceries since June: 2 000 → 4 000 → 4 000 …, capped at 2 × amount.
        val generous = budget(2_000, setOf(groceries.id), rollover = true, startsOn = LocalDate.of(2026, 6, 1))
        engine.status(generous).limit shouldBe Money.ofMajor(4_000, inr)

        val history = engine.history(fromAugust, engine.periods().current(fromAugust, clock.today()), count = 6)
        history.map { it.period.key } shouldContainExactly listOf("2026-08", "2026-09")
        history.map { it.limit } shouldContainExactly listOf(Money.ofMajor(8_000, inr), Money.ofMajor(13_000, inr))
    }

    @Test
    fun `the first two days of a period never read as over pace unless the limit is already gone`() = runBlocking<Unit> {
        val early = FixedClock(Instant.parse("2026-09-05T10:00:00Z"), zone)
        val engine = BudgetEngine(spending, categories, preferences, early)
        spend(LocalDate.of(2026, 9, 5), 900)
        engine.status(budget(10_000, setOf(delivery.id))).let {
            it.elapsedDays shouldBe 1
            it.projected shouldBe Money.ofMajor(27_000, inr)
            it.pace shouldBe BudgetPace.ON_TRACK
        }
        engine.status(budget(500, setOf(delivery.id))).pace shouldBe BudgetPace.OVER
    }
}
