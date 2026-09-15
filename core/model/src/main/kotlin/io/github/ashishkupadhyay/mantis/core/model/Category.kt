package io.github.ashishkupadhyay.mantis.core.model

enum class CategoryKind { EXPENSE, INCOME, TRANSFER, SYSTEM }

/**
 * Two-level taxonomy: a group has `parentId == null`; a leaf points at its group (FR-CAT-1).
 * System categories carry a stable [key] (`food.delivery`) used by the classifier, sync and AppFunctions;
 * user-created categories have no key and are only reachable through rules and merchant memory (doc 04 §3.1).
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val parentId: CategoryId? = null,
    val key: String? = null,
    val icon: String = "category",
    val colorSeed: Int = 0,
    val isSystem: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val meta: SyncMeta,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(!isSystem || key != null) { "System categories must have a key" }
    }

    val isGroup: Boolean get() = parentId == null

    /** The palette slot the user picked, or `null` for the automatic (hashed) colour; stored 1-based in [colorSeed]. */
    val paletteIndex: Int? get() = (colorSeed - 1).takeIf { colorSeed > 0 }

    companion object {
        /** [colorSeed] value for "let Mantis pick". */
        const val AUTO_COLOR = 0

        fun colorSeedFor(paletteIndex: Int?): Int = paletteIndex?.plus(1) ?: AUTO_COLOR
    }
}

/** Where a transaction's category label came from; precedence for sync merges is [rank] (doc 03 §6.2). */
enum class CategorySource(val rank: Int) {
    /** Manual choice or per-user merchant memory. */
    USER(rank = 5),
    USER_RULE(rank = 5),
    MERCHANT_DB(rank = 4),
    ON_DEVICE_MODEL(rank = 3),

    /** The user's own LLM provider (FR-CAT-10) — a suggestion, never the user's decision. */
    LLM(rank = 3),

    /** Category column present in an imported CSV. */
    IMPORT(rank = 2),
    NONE(rank = 0),
    ;

    fun outranks(other: CategorySource): Boolean = rank > other.rank
}
