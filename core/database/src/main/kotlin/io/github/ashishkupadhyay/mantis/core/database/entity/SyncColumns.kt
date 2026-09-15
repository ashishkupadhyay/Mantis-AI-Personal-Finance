package io.github.ashishkupadhyay.mantis.core.database.entity

/**
 * Sync bookkeeping embedded in every synced table (doc 02 §5.1): flattened into the columns
 * `updatedAt`, `version`, `deletedAt`, `dirty`, `deviceId`. Mirrors the domain `SyncMeta`.
 */
data class SyncColumns(
    val updatedAt: Long,
    val version: Int = 0,
    val deletedAt: Long? = null,
    val dirty: Boolean = true,
    val deviceId: String,
)
