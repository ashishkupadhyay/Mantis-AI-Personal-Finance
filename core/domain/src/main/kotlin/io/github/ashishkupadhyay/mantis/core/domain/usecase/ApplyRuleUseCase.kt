package io.github.ashishkupadhyay.mantis.core.domain.usecase

import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleMatcher
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import javax.inject.Inject

/**
 * "Apply to existing transactions" (FR-CAT-6): recategorizes every live expense/income the rule matches, except
 * rows the user labelled by hand — a rule never outranks a manual choice (doc 03 §6.2 precedence). Returns how many
 * rows changed. Scans at most [SCAN_LIMIT] rows, newest first, which covers years of personal history.
 */
class ApplyRuleUseCase @Inject constructor(private val transactions: TransactionRepository) {

    suspend operator fun invoke(rule: CategoryRule): Int {
        val matcher = RuleMatcher(listOf(rule))
        val candidates = transactions.list(TransactionFilter(types = setOf(TransactionType.EXPENSE, TransactionType.INCOME)), SCAN_LIMIT)
        val hits = candidates.filter { row ->
            row.categorySource != CategorySource.USER &&
                row.categoryId != rule.categoryId &&
                matcher.match(row.descriptionRaw, row.merchantNormalized) != null
        }
        if (hits.isEmpty()) return 0
        return transactions.updateAll(hits.map { it.id }.toSet()) {
            it.copy(categoryId = rule.categoryId, categorySource = CategorySource.USER_RULE, categoryConfidence = 1f, needsReview = false)
        }
    }

    private companion object {
        const val SCAN_LIMIT = 20_000
    }
}
