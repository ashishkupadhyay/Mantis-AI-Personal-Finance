package io.github.ashishkupadhyay.mantis.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import io.github.ashishkupadhyay.mantis.core.database.dao.AccountDao
import io.github.ashishkupadhyay.mantis.core.database.dao.BudgetDao
import io.github.ashishkupadhyay.mantis.core.database.dao.CategoryDao
import io.github.ashishkupadhyay.mantis.core.database.dao.ImportDao
import io.github.ashishkupadhyay.mantis.core.database.dao.MerchantDao
import io.github.ashishkupadhyay.mantis.core.database.dao.ModelRegistryDao
import io.github.ashishkupadhyay.mantis.core.database.dao.OutboxDao
import io.github.ashishkupadhyay.mantis.core.database.dao.SyncCursorDao
import io.github.ashishkupadhyay.mantis.core.database.dao.TagDao
import io.github.ashishkupadhyay.mantis.core.database.dao.TransactionDao
import io.github.ashishkupadhyay.mantis.core.database.entity.AccountEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetAccountCrossRef
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetAlertLogEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetCategoryCrossRef
import io.github.ashishkupadhyay.mantis.core.database.entity.BudgetEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.CategoryRuleEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ImportBatchEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ImportPresetEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantAliasEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.MerchantEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.ModelRegistryEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.OutboxEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.SyncCursorEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TagEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionFtsEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionSplitEntity
import io.github.ashishkupadhyay.mantis.core.database.entity.TransactionTagCrossRef

/**
 * Schema v1: the M1 table set (doc 02 §5.2). Every version ships an exported schema under `schemas/` and a tested
 * migration (doc 02 §5.4); `exportSchema` stays true so auto-migrations and the migration test helper work.
 */
@Database(
    version = MantisDatabase.VERSION,
    exportSchema = true,
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        TransactionFtsEntity::class,
        TransactionSplitEntity::class,
        TransactionTagCrossRef::class,
        TagEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        BudgetEntity::class,
        BudgetCategoryCrossRef::class,
        BudgetAccountCrossRef::class,
        BudgetAlertLogEntity::class,
        ImportBatchEntity::class,
        ImportPresetEntity::class,
        OutboxEntity::class,
        SyncCursorEntity::class,
        ModelRegistryEntity::class,
    ],
)
abstract class MantisDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun merchantDao(): MerchantDao
    abstract fun budgetDao(): BudgetDao
    abstract fun importDao(): ImportDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun modelRegistryDao(): ModelRegistryDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "mantis.db"
    }
}
