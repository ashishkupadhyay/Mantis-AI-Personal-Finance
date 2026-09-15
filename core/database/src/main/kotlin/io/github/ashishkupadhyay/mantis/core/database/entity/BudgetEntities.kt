package io.github.ashishkupadhyay.mantis.core.database.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.ashishkupadhyay.mantis.core.model.BudgetPeriodType

/** `budgets` (FR-BUD-1). `thresholdsJson` is a JSON int array, e.g. `[50,80,100]`. */
@Entity(tableName = "budgets", indices = [Index("period")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val period: BudgetPeriodType,
    val customStart: String?,
    val customEnd: String?,
    val rollover: Boolean,
    val thresholdsJson: String,
    val alertOnProjectedOverspend: Boolean,
    val snoozedUntilPeriodKey: String?,
    /** ISO date the budget counts from (rollover base); null for rows written before the column existed. */
    val startsOn: String?,
    @Embedded val sync: SyncColumns,
)

/** `budget_categories`: no rows = all spending. */
@Entity(
    tableName = "budget_categories",
    primaryKeys = ["budgetId", "categoryId"],
    foreignKeys = [
        ForeignKey(BudgetEntity::class, ["id"], ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE, deferred = true),
    ],
    indices = [Index("categoryId")],
)
data class BudgetCategoryCrossRef(val budgetId: String, val categoryId: String)

/** `budget_accounts`: no rows = every account. */
@Entity(
    tableName = "budget_accounts",
    primaryKeys = ["budgetId", "accountId"],
    foreignKeys = [
        ForeignKey(BudgetEntity::class, ["id"], ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE, deferred = true),
    ],
    indices = [Index("accountId")],
)
data class BudgetAccountCrossRef(val budgetId: String, val accountId: String)

/** `budget_alert_log`: one row per (budget, period, threshold) so a threshold notifies once (FR-BUD-4). Device-only. */
@Entity(
    tableName = "budget_alert_log",
    primaryKeys = ["budgetId", "periodKey", "threshold"],
    foreignKeys = [ForeignKey(BudgetEntity::class, ["id"], ["budgetId"], onDelete = ForeignKey.CASCADE)],
)
data class BudgetAlertLogEntity(
    val budgetId: String,
    val periodKey: String,
    val threshold: Int,
    val notifiedAt: Long,
)
