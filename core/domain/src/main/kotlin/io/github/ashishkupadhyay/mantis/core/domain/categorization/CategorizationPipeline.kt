package io.github.ashishkupadhyay.mantis.core.domain.categorization

import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.CategorySuggestion
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import java.time.Instant

/** What the pipeline sees for one draft: the raw narration and the context that helps disambiguate it. */
data class CategorizationRequest(
    val descriptionRaw: String,
    val amount: Money,
    val type: TransactionType,
    val accountId: AccountId,
    val postedAt: Instant,
)

/**
 * The rules → memory → dictionary → classifier chain of doc 02 §6.1. Returns `null` when nothing is confident
 * enough, in which case the transaction stays uncategorized. Pure and synchronous per request; the LLM fallback
 * is a separate, asynchronous queue that never blocks a write.
 */
fun interface CategorizationPipeline {
    suspend fun categorize(request: CategorizationRequest): CategorySuggestion?
}

/** M1 stand-in until `core:ml` lands (WP-2.x): every manual entry without a chosen category stays uncategorized. */
object NoCategorizationPipeline : CategorizationPipeline {
    override suspend fun categorize(request: CategorizationRequest): CategorySuggestion? = null
}
