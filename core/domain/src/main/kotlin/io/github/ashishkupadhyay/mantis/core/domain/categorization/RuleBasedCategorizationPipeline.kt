package io.github.ashishkupadhyay.mantis.core.domain.categorization

import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.CategorySuggestion
import javax.inject.Inject

/**
 * The first stage of doc 02 §6.1 on its own: user rules, priority descending, confidence 1.0. Everything after it
 * (merchant memory, dictionary, classifier, LLM fallback) arrives with `core:ml` in M2 and slots in behind
 * [fallback]. Rules are re-read per request so a rule created a moment ago applies to the next entry.
 */
class RuleBasedCategorizationPipeline @Inject constructor(
    private val categories: CategoryRepository,
) : CategorizationPipeline {

    private val fallback: CategorizationPipeline = NoCategorizationPipeline

    override suspend fun categorize(request: CategorizationRequest): CategorySuggestion? {
        val rule = RuleMatcher(categories.rules()).match(request.descriptionRaw, merchant = null)
        if (rule != null) return CategorySuggestion(rule.categoryId, CategorySource.USER_RULE, confidence = 1f)
        return fallback.categorize(request)
    }
}
