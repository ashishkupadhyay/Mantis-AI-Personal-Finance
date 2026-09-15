package io.github.ashishkupadhyay.mantis.core.domain.categorization

import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration

class RuleMatcherTest {

    private fun rule(
        type: RuleMatchType,
        pattern: String,
        category: String,
        priority: Int = 0,
        field: RuleField = RuleField.DESCRIPTION,
    ) = CategoryRule(RuleId(category + pattern), type, pattern, field, CategoryId(category), priority, meta = testMeta())

    @Test
    fun `FR_CAT_6 each match type behaves as named and is case-insensitive`() {
        val matcher = RuleMatcher(
            listOf(
                rule(RuleMatchType.CONTAINS, "swiggy", "delivery"),
                rule(RuleMatchType.STARTS_WITH, "UPI/AMZN", "shopping"),
                rule(RuleMatchType.REGEX, "^(cult|gold'?s) ?gym", "fitness"),
                rule(RuleMatchType.MERCHANT, "blinkit", "groceries", field = RuleField.MERCHANT),
            ),
        )

        matcher.match("Paid to SWIGGY INSTAMART", null)?.categoryId shouldBe CategoryId("delivery")
        matcher.match("upi/amzn/123", null)?.categoryId shouldBe CategoryId("shopping")
        matcher.match("xx upi/amzn", null) shouldBe null
        matcher.match("Cult Gym membership", null)?.categoryId shouldBe CategoryId("fitness")
        matcher.match("BLINKIT ORDER", "Blinkit")?.categoryId shouldBe CategoryId("groceries")
        matcher.match("BLINKIT ORDER", null) shouldBe null // merchant rules need a merchant
        matcher.match("nothing here", "other") shouldBe null
    }

    @Test
    fun `higher priority wins, then the older rule`() {
        val matcher = RuleMatcher(
            listOf(
                rule(RuleMatchType.CONTAINS, "amazon", "shopping", priority = 1),
                rule(RuleMatchType.CONTAINS, "amazon prime", "subscriptions", priority = 5),
            ),
        )
        matcher.match("AMAZON PRIME VIDEO", null)?.categoryId shouldBe CategoryId("subscriptions")
        matcher.match("AMAZON.IN", null)?.categoryId shouldBe CategoryId("shopping")
    }

    @Test
    fun `NFR_20b a hostile regex neither compiles into a hang nor crashes matching`() {
        val evil = "(a+)+$"
        val matcher = RuleMatcher(listOf(rule(RuleMatchType.REGEX, evil, "x")))
        val input = "a".repeat(RuleMatcher.MAX_INPUT * 4) + "!"
        assertTimeoutPreemptively(Duration.ofSeconds(2)) { matcher.match(input, null) }
        RuleMatcher(listOf(rule(RuleMatchType.REGEX, "(unclosed", "x"))).size shouldBe 0
    }

    @Test
    fun `validator rejects blanks, long patterns and bad regexes with a readable message`() {
        RuleValidator.validate(RuleMatchType.CONTAINS, "  ").shouldBeInstanceOf<AppError.Validation>().field shouldBe "pattern"
        RuleValidator.validate(RuleMatchType.CONTAINS, "x".repeat(CategoryRule.MAX_PATTERN_LENGTH + 1))
            .shouldBeInstanceOf<AppError.Validation>()
        RuleValidator.validate(RuleMatchType.REGEX, "(unclosed").shouldBeInstanceOf<AppError.Validation>().message shouldBe
            "Not a valid expression: missing closing )"
        RuleValidator.validate(RuleMatchType.REGEX, "^swiggy\\b") shouldBe null
        RuleValidator.validate(RuleMatchType.CONTAINS, "zomato") shouldBe null
    }
}
