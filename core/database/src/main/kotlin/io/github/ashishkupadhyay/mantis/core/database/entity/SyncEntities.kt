package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class OutboxOp { UPSERT, DELETE }

/** `outbox`: the sync push queue (doc 03 §6), written in the same transaction as the change it describes. */
@Entity(tableName = "outbox", indices = [Index("entityType", "entityId")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityType: String,
    val entityId: String,
    val op: OutboxOp,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** `sync_cursors`: per-entity-type pull cursor from the server. */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val entityType: String,
    val serverCursor: String,
    val updatedAt: Long,
)

/** `model_registry`: installed ML assets (classifier, dictionaries) with their verified checksums (NFR-20a). */
@Entity(tableName = "model_registry")
data class ModelRegistryEntity(
    @PrimaryKey val modelName: String,
    val version: String,
    val path: String,
    val checksum: String,
    val labelsJson: String,
    val thresholdsJson: String,
    val installedAt: Long,
)
