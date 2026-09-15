package io.github.ashishkupadhyay.mantis.core.model

/**
 * What the categorization pipeline says about a transaction (doc 02 §6.1): the label, where it came from and how
 * sure it is, plus runners-up so the UI can offer top-3 chips (FR-TXN-12).
 */
data class CategorySuggestion(
    val categoryId: CategoryId,
    val source: CategorySource,
    val confidence: Float,
    val alternatives: List<Alternative> = emptyList(),
) {
    init {
        require(confidence in 0f..1f) { "confidence must be within 0..1" }
    }

    data class Alternative(val categoryId: CategoryId, val probability: Float)

    /** Auto-assigned silently, or shown as "suggested" and queued for review (doc 02 §6.1 thresholds). */
    val needsReview: Boolean get() = confidence < AUTO_ASSIGN

    companion object {
        /** ≥ this: auto-assign; below: flagged "suggested". */
        const val AUTO_ASSIGN = 0.75f

        /** Below this the pipeline leaves the transaction uncategorized. */
        const val SUGGEST = 0.5f
    }
}
