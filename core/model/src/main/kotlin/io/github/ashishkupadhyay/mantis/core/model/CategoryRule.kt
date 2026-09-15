package io.github.ashishkupadhyay.mantis.core.model

@JvmInline value class RuleId(val value: String)

enum class RuleMatchType { CONTAINS, STARTS_WITH, REGEX, MERCHANT }

enum class RuleField { DESCRIPTION, MERCHANT }

/**
 * A user categorisation rule (FR-CAT-6): evaluated by [priority] descending before any model runs.
 * Patterns are length-capped and regexes run on a linear-time engine (NFR-20b), enforced by the matcher.
 */
data class CategoryRule(
    val id: RuleId,
    val matchType: RuleMatchType,
    val pattern: String,
    val field: RuleField = RuleField.DESCRIPTION,
    val categoryId: CategoryId,
    val priority: Int = 0,
    val createdFromTransactionId: TransactionId? = null,
    val hitCount: Int = 0,
    val meta: SyncMeta,
) {
    init {
        require(pattern.isNotBlank()) { "Rule pattern must not be blank" }
        require(pattern.length <= MAX_PATTERN_LENGTH) { "Rule pattern longer than $MAX_PATTERN_LENGTH" }
    }

    companion object {
        const val MAX_PATTERN_LENGTH = 200
    }
}
