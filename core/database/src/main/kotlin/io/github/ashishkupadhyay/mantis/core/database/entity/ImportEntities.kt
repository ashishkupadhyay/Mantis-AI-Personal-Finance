package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** `import_batches` (FR-IMP-8): undo = delete every transaction whose `importBatchId` is this id. */
@Entity(
    tableName = "import_batches",
    foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT, deferred = true)],
    indices = [Index("accountId", "createdAt"), Index("fileHash")],
)
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val sourceName: String,
    val presetId: String?,
    val fileHash: String,
    val rowCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val mergedCount: Int,
    val errorCount: Int,
    val createdAt: Long,
    val undoneAt: Long?,
)

/** `import_presets` (FR-IMP-2): bundled presets are refreshed from the signed asset; user presets are saved mappings. */
@Entity(tableName = "import_presets", indices = [Index("isBundled")])
data class ImportPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val signatureJson: String,
    val mappingJson: String,
    val dateFormat: String,
    val isBundled: Boolean,
    val updatedAt: Long,
)
