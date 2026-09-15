package io.github.ashishkupadhyay.mantis.core.model

/**
 * A user-defined label on a transaction or line item (FR-VIEW-4). Tags are never assigned by a model —
 * only suggested by rules the user accepts (FR-RCP-17).
 */
data class Tag(
    val id: TagId,
    val name: String,
    val colorSeed: Int = 0,
    val emoji: String? = null,
    val description: String? = null,
    val meta: SyncMeta,
) {
    init {
        require(name.isNotBlank()) { "Tag name must not be blank" }
        require(name.length <= MAX_NAME) { "Tag name too long" }
    }

    /** Case-insensitive, whitespace-collapsed form used for uniqueness (`#Goa Trip` == `#goa-trip`). */
    val normalizedName: String get() = normalize(name)

    companion object {
        private const val MAX_NAME = 40
        fun normalize(name: String): String = name.trim().removePrefix("#").lowercase().replace(Regex("[\\s_]+"), "-")
    }
}
