package io.github.ashishkupadhyay.mantis.core.model

import java.time.Instant

/** One atomic, undoable import (FR-IMP-8): every row it created carries this batch's id. */
data class ImportBatch(
    val id: ImportBatchId,
    val accountId: AccountId,
    val sourceName: String,
    val presetId: String? = null,
    val fileHash: String,
    val rowCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val mergedCount: Int = 0,
    val errorCount: Int,
    val createdAt: Instant,
    val undoneAt: Instant? = null,
) {
    val isUndone: Boolean get() = undoneAt != null

    init {
        require(rowCount >= 0 && importedCount >= 0 && duplicateCount >= 0 && mergedCount >= 0 && errorCount >= 0)
        require(importedCount + duplicateCount + mergedCount + errorCount <= rowCount) { "counts exceed rows" }
    }
}
