package io.github.ashishkupadhyay.mantis.feature.transactions.list

import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.feature.transactions.BANK
import io.github.ashishkupadhyay.mantis.feature.transactions.CARD
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

class FilterQueryTest {

    private val categories = runBlocking { FakeCategoryRepository.withDefaults().categories() }
    private val goa = Tag(TagId("t1"), "Goa Trip", meta = testMeta())

    private fun parse(raw: String) = FilterQuery.parse(raw, categories, listOf(BANK, CARD), listOf(goa))

    @Test
    fun `FR_TXN_5 links resolve keys, ids, names, dates and flags`() {
        val delivery = categories.first { it.key == "food.delivery" }
        val filter = parse(
            "category=food.delivery&account=card&type=expense&from=2026-09-01&to=2026-09-30&tag=goa-trip&q=zomato order&review=1",
        )

        filter.categoryIds shouldBe setOf(delivery.id)
        filter.accountIds shouldBe setOf(CARD.id)
        filter.types shouldBe setOf(TransactionType.EXPENSE)
        filter.dateRange shouldBe DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        filter.tagIds shouldBe setOf(goa.id)
        filter.query shouldBe "zomato order"
        filter.needsReviewOnly shouldBe true
        filter.includeExcluded shouldBe true
    }

    @Test
    fun `unknown values and malformed input degrade to a plain list`() {
        parse("category=nope&account=missing&type=bogus&from=not-a-date&garbage&=x") shouldBe TransactionFilter.ALL
        parse("") shouldBe TransactionFilter.ALL
        parse("uncategorized=1&excluded=0").let {
            it.uncategorizedOnly shouldBe true
            it.includeExcluded shouldBe false
        }
    }

    @Test
    fun `open-ended dates and inverted ranges are handled`() {
        parse("from=2026-09-01").dateRange?.start shouldBe LocalDate.of(2026, 9, 1)
        parse("to=2026-09-01").dateRange?.endInclusive shouldBe LocalDate.of(2026, 9, 1)
        parse("from=2026-09-30&to=2026-09-01").dateRange shouldBe null
        parse("category=food.delivery,food.groceries").categoryIds.size shouldBe 2
    }
}
