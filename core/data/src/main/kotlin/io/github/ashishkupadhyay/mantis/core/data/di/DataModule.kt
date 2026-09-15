package io.github.ashishkupadhyay.mantis.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultAccountRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultBudgetRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultCategoryRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultDevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultImportRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultPreferencesRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultSpendingRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultTagRepository
import io.github.ashishkupadhyay.mantis.core.data.repository.DefaultTransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.categorization.CategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleBasedCategorizationPipeline
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRepository
import io.github.ashishkupadhyay.mantis.core.domain.imports.StatementImporter
import io.github.ashishkupadhyay.mantis.core.importer.CsvStatementImporter
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.BudgetRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.SpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.security.AppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.security.DefaultAppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.transaction.TransactionWriteObserver

/** Repository interfaces (doc 02 §4.3) bound to their Room/DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun preferences(impl: DefaultPreferencesRepository): PreferencesRepository

    @Binds abstract fun accounts(impl: DefaultAccountRepository): AccountRepository

    @Binds abstract fun categories(impl: DefaultCategoryRepository): CategoryRepository

    @Binds abstract fun tags(impl: DefaultTagRepository): TagRepository

    @Binds abstract fun transactions(impl: DefaultTransactionRepository): TransactionRepository

    @Binds abstract fun spending(impl: DefaultSpendingRepository): SpendingRepository

    @Binds abstract fun budgets(impl: DefaultBudgetRepository): BudgetRepository

    @Binds abstract fun development(impl: DefaultDevelopmentRepository): DevelopmentRepository

    @Binds abstract fun imports(impl: DefaultImportRepository): ImportRepository

    /** The parsing half of imports lives in the pure-JVM `core:importer`; this is its only Hilt binding. */
    @Binds abstract fun statementImporter(impl: CsvStatementImporter): StatementImporter

    @Binds abstract fun appLock(impl: DefaultAppLockManager): AppLockManager

    /** Budgets (WP-1.6) and insights (M2) add observers with `@IntoSet`; until then the set is simply empty. */
    @Multibinds abstract fun transactionWriteObservers(): Set<TransactionWriteObserver>

    /** User rules only until `core:ml` adds merchant memory, the dictionary and the classifier (M2). */
    @Binds abstract fun categorizationPipeline(impl: RuleBasedCategorizationPipeline): CategorizationPipeline
}
