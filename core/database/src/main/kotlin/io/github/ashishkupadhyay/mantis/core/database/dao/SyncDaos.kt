package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.ashishkupadhyay.mantis.core.database.entity.ImportBatchEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ImportPresetEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ModelRegistryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.OutboxEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncCursorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {

    @Upsert
    suspend fun upsertBatch(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getBatch(id: String): ImportBatchEntity?

    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE accountId = :accountId AND fileHash = :fileHash AND undoneAt IS NULL LIMIT 1")
    suspend fun findByFileHash(accountId: String, fileHash: String): ImportBatchEntity?

    @Query("UPDATE import_batches SET undoneAt = :at WHERE id = :id")
    suspend fun markUndone(id: String, at: Long)

    @Upsert
    suspend fun upsertPresets(presets: List<ImportPresetEntity>)

    @Query("SELECT * FROM import_presets ORDER BY isBundled DESC, name")
    suspend fun presets(): List<ImportPresetEntity>

    @Query("DELETE FROM import_presets WHERE isBundled = 1 AND id NOT IN (:keepIds)")
    suspend fun deleteStaleBundledPresets(keepIds: List<String>): Int
}

@Dao
interface OutboxDao {

    @Insert
    suspend fun enqueue(entry: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY seq LIMIT :limit")
    suspend fun next(limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    suspend fun ack(seqs: List<Long>): Int

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE seq = :seq")
    suspend fun recordFailure(seq: Long, error: String?)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeSize(): Flow<Int>

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface SyncCursorDao {

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursors WHERE entityType = :entityType")
    suspend fun get(entityType: String): SyncCursorEntity?

    @Query("DELETE FROM sync_cursors")
    suspend fun clear()
}

@Dao
interface ModelRegistryDao {

    @Upsert
    suspend fun upsert(model: ModelRegistryEntity)

    @Query("SELECT * FROM model_registry WHERE modelName = :modelName")
    suspend fun get(modelName: String): ModelRegistryEntity?

    @Query("SELECT * FROM model_registry")
    fun observeAll(): Flow<List<ModelRegistryEntity>>

    @Query("DELETE FROM model_registry WHERE modelName = :modelName")
    suspend fun delete(modelName: String)
}
