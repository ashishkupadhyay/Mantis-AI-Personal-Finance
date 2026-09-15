package io.github.ashishkupadhyay.mantis.core.domain.categorization

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType

/**
 * Evaluates user rules (FR-CAT-6) in priority order, highest first. `CONTAINS` / `STARTS_WITH` are case-insensitive
 * on the chosen field; `MERCHANT` is an exact, case-insensitive match on the normalised merchant; `REGEX` runs on
 * RE2/J — linear time, so a hostile pattern cannot stall the app (NFR-20b) — over input capped at [MAX_INPUT] chars.
 * Patterns that fail to compile are skipped here and rejected at creation by [RuleValidator].
 */
class RuleMatcher(rules: List<CategoryRule>) {

    private val compiled: List<Compiled> = rules
        .filter { !it.meta.isDeleted }
        .sortedWith(compareByDescending<CategoryRule> { it.priority }.thenBy { it.meta.updatedAt })
        .mapNotNull { rule ->
            val regex = if (rule.matchType == RuleMatchType.REGEX) RuleValidator.compile(rule.pattern) ?: return@mapNotNull null else null
            Compiled(rule, regex)
        }

    val size: Int get() = compiled.size

    /** The first rule that matches [description] (raw narration) or [merchant] (normalised, may be null). */
    fun match(description: String, merchant: String?): CategoryRule? =
        compiled.firstOrNull { it.matches(description, merchant) }?.rule

    private class Compiled(val rule: CategoryRule, private val regex: Pattern?) {
        fun matches(description: String, merchant: String?): Boolean {
            val subject = when (rule.field) {
                RuleField.DESCRIPTION -> description
                RuleField.MERCHANT -> merchant ?: return false
            }.take(MAX_INPUT)
            return when (rule.matchType) {
                RuleMatchType.CONTAINS -> subject.contains(rule.pattern, ignoreCase = true)
                RuleMatchType.STARTS_WITH -> subject.startsWith(rule.pattern, ignoreCase = true)
                RuleMatchType.MERCHANT -> (merchant ?: subject).equals(rule.pattern, ignoreCase = true)
                RuleMatchType.REGEX -> regex?.matcher(subject)?.find() == true
            }
        }
    }

    companion object {
        const val MAX_INPUT = 256
    }
}

/** Creation-time checks for rule patterns (NFR-20b): length cap and, for regexes, RE2/J compilation. */
object RuleValidator {
    fun validate(matchType: RuleMatchType, pattern: String): AppError? {
        val trimmed = pattern.trim()
        return when {
            trimmed.isEmpty() -> AppError.Validation("pattern", "Enter something to match")
            trimmed.length > CategoryRule.MAX_PATTERN_LENGTH ->
                AppError.Validation("pattern", "Keep the pattern under ${CategoryRule.MAX_PATTERN_LENGTH} characters")
            matchType == RuleMatchType.REGEX -> compileError(trimmed)?.let { AppError.Validation("pattern", it) }
            else -> null
        }
    }

    /** RE2/J pattern, case-insensitive; `null` when it does not compile (the failure *is* the answer here). */
    @Suppress("SwallowedException")
    fun compile(pattern: String): Pattern? = try {
        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
    } catch (e: PatternSyntaxException) {
        null
    }

    private fun compileError(pattern: String): String? = try {
        Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        null
    } catch (e: PatternSyntaxException) {
        "Not a valid expression: ${e.description}"
    }
}
