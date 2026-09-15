package io.github.ashishkupadhyay.mantis.core.database.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetAccountCrossRef
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetAlertLogEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetCategoryCrossRef
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/** A budget with its category and account scopes. */
data class BudgetWithScope(
    @Embedded val budget: BudgetEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["budgetId"]) val categories: List<BudgetCategoryCrossRef>,
    @Relation(parentColumns = ["id"], entityColumns = ["budgetId"]) val accounts: List<BudgetAccountCrossRef>,
)

@Dao
interface BudgetDao {

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Transaction
    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<BudgetWithScope>>

    @Transaction
    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<BudgetWithScope>

    @Transaction
    @Query("SELECT * FROM budgets WHERE id = :id")
    fun observeById(id: String): Flow<BudgetWithScope?>

    @Transaction
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetWithScope?

    @Query("UPDATE budgets SET deletedAt = :at, dirty = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, at: Long): Int

    @Query("UPDATE budgets SET snoozedUntilPeriodKey = :periodKey, dirty = 1, updatedAt = :at WHERE id = :id")
    suspend fun snooze(id: String, periodKey: String?, at: Long)

    @Upsert
    suspend fun upsertCategoryRefs(refs: List<BudgetCategoryCrossRef>)

    @Upsert
    suspend fun upsertAccountRefs(refs: List<BudgetAccountCrossRef>)

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun deleteCategoryRefs(budgetId: String)

    @Query("DELETE FROM budget_accounts WHERE budgetId = :budgetId")
    suspend fun deleteAccountRefs(budgetId: String)

    @Transaction
    suspend fun replace(budget: BudgetEntity, categoryIds: Set<String>, accountIds: Set<String>) {
        upsert(budget)
        deleteCategoryRefs(budget.id)
        if (categoryIds.isNotEmpty()) upsertCategoryRefs(categoryIds.map { BudgetCategoryCrossRef(budget.id, it) })
        deleteAccountRefs(budget.id)
        if (accountIds.isNotEmpty()) upsertAccountRefs(accountIds.map { BudgetAccountCrossRef(budget.id, it) })
    }

    // --- alert log (device-only) -------------------------------------------------------------------------------

    /** Returns false when the (budget, period, threshold) alert was already logged, so the caller skips the notification. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun logAlert(entry: BudgetAlertLogEntity): Long

    @Query("SELECT threshold FROM budget_alert_log WHERE budgetId = :budgetId AND periodKey = :periodKey")
    suspend fun loggedThresholds(budgetId: String, periodKey: String): List<Int>

    @Query("DELETE FROM budget_alert_log WHERE notifiedAt < :before")
    suspend fun purgeAlertLog(before: Long): Int
}
