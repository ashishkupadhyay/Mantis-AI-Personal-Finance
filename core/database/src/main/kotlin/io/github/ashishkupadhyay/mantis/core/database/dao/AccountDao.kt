package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.ashishkupadhyay.mantis.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY isArchived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY sortOrder, name")
    suspend fun active(): List<AccountEntity>

    @Query("UPDATE accounts SET isArchived = :archived, dirty = 1, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long)

    @Query("UPDATE accounts SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>
}
