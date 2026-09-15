package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryRuleEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE `key` = :key")
    suspend fun getByKey(key: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY kind, sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY kind, sortOrder, name")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE isSystem = 1")
    suspend fun systemCount(): Int

    @Query("UPDATE categories SET isHidden = :hidden, dirty = 1, updatedAt = :at WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, at: Long)

    @Query("UPDATE categories SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND isSystem = 0 AND deletedAt IS NULL")
    suspend fun softDeleteUserCategory(id: String, at: Long): Int

    // --- rules -----------------------------------------------------------------------------------------------

    @Upsert
    suspend fun upsertRule(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules WHERE deletedAt IS NULL ORDER BY priority DESC, updatedAt DESC")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules WHERE deletedAt IS NULL ORDER BY priority DESC, updatedAt DESC")
    suspend fun rules(): List<CategoryRuleEntity>

    @Query("UPDATE category_rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun recordHit(id: String)

    @Query("UPDATE category_rules SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteRule(id: String, at: Long): Int
}

@Dao
interface TagDao {

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE nameNormalized = :nameNormalized")
    suspend fun byNormalizedName(nameNormalized: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TagEntity>

    @Query("UPDATE tags SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long): Int
}
