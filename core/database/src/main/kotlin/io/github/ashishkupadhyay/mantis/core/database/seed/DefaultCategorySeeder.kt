package io.github.ashishkupadhyay.mantis.core.database.seed

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncColumns
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import javax.inject.Inject

/**
 * Seeds the bundled taxonomy (doc 01 Appendix A) once per database. Idempotent: rows are keyed by the stable
 * `key`, so re-running adds only leaves that were added to [DefaultTaxonomy] since the last run — never renames
 * or deletes user-visible categories.
 */
class DefaultCategorySeeder @Inject constructor(
    private val database: MantisDatabase,
    private val clock: Clock,
    private val ids: UuidV7,
) {

    /** Returns the number of categories inserted. */
    suspend fun seed(deviceId: String): Int {
        val dao = database.categoryDao()
        val existing = dao.all().mapNotNull { it.key }.toSet()
        val now = clock.epochMillis()
        val rows = mutableListOf<CategoryEntity>()
        DefaultTaxonomy.groups.forEachIndexed { groupIndex, group ->
            val groupId = if (group.key in existing) dao.getByKey(group.key)?.id else null
            val resolvedGroupId = groupId ?: ids.nextString().also { id ->
                rows += CategoryEntity(
                    id = id,
                    parentId = null,
                    key = group.key,
                    name = group.name,
                    icon = group.icon,
                    colorSeed = Category.AUTO_COLOR,
                    kind = group.kind,
                    isSystem = true,
                    isHidden = false,
                    sortOrder = groupIndex,
                    sync = SyncColumns(updatedAt = now, deviceId = deviceId),
                )
            }
            group.leaves.forEachIndexed { leafIndex, leaf ->
                if (leaf.key !in existing) {
                    rows += CategoryEntity(
                        id = ids.nextString(),
                        parentId = resolvedGroupId,
                        key = leaf.key,
                        name = leaf.name,
                        icon = leaf.icon,
                        colorSeed = Category.AUTO_COLOR,
                        kind = group.kind,
                        isSystem = true,
                        isHidden = false,
                        sortOrder = leafIndex,
                        sync = SyncColumns(updatedAt = now, deviceId = deviceId),
                    )
                }
            }
        }
        if (rows.isNotEmpty()) {
            database.useWriterConnection { connection ->
                connection.immediateTransaction { dao.upsertAll(rows) }
            }
        }
        return rows.size
    }
}
