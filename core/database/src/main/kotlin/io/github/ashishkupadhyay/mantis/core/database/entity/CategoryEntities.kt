package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind

/** `categories`: two levels only (`parentId == null` is a group). System rows carry a stable `key` (doc 04 §3.1). */
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["parentId"], onDelete = ForeignKey.RESTRICT, deferred = true)],
    indices = [Index("parentId"), Index("key", unique = true), Index("kind", "isHidden", "sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val key: String?,
    val name: String,
    val icon: String,
    val colorSeed: Int,
    val kind: CategoryKind,
    val isSystem: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    @Embedded val sync: SyncColumns,
)

enum class RuleMatchType { CONTAINS, STARTS_WITH, REGEX, MERCHANT }

enum class RuleField { DESCRIPTION, MERCHANT }

/** `category_rules` (FR-CAT-6): evaluated by priority descending; `pattern` is capped at 200 chars (NFR-20b). */
@Entity(
    tableName = "category_rules",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE, deferred = true)],
    indices = [Index("categoryId"), Index("priority")],
)
data class CategoryRuleEntity(
    @PrimaryKey val id: String,
    val matchType: RuleMatchType,
    val pattern: String,
    val field: RuleField,
    val categoryId: String,
    val priority: Int,
    val createdFromTransactionId: String?,
    val hitCount: Int,
    @Embedded val sync: SyncColumns,
)

/** `tags` (FR-VIEW-4): user-defined only; `nameNormalized` is the case-insensitive uniqueness key. */
@Entity(tableName = "tags", indices = [Index("nameNormalized", unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameNormalized: String,
    val colorSeed: Int,
    val emoji: String?,
    val description: String?,
    @Embedded val sync: SyncColumns,
)
