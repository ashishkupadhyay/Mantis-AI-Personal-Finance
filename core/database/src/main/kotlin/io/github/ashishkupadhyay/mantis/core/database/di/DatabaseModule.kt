package io.github.ashishkupadhyay.mantis.core.database.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ashishkupadhyay.mantis.core.database.MantisDatabase
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * One SQLite build everywhere via [BundledSQLiteDriver]; queries run on IO. Destructive migration is allowed
     * only in debuggable builds (doc 02 §5.4) — release builds must ship a migration or fail loudly.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MantisDatabase {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return Room.databaseBuilder<MantisDatabase>(context, MantisDatabase.FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .apply { if (debuggable) fallbackToDestructiveMigration(dropAllTables = true) }
            .build()
    }

    @Provides fun accountDao(db: MantisDatabase) = db.accountDao()

    @Provides fun transactionDao(db: MantisDatabase) = db.transactionDao()

    @Provides fun categoryDao(db: MantisDatabase) = db.categoryDao()

    @Provides fun tagDao(db: MantisDatabase) = db.tagDao()

    @Provides fun merchantDao(db: MantisDatabase) = db.merchantDao()

    @Provides fun budgetDao(db: MantisDatabase) = db.budgetDao()

    @Provides fun importDao(db: MantisDatabase) = db.importDao()

    @Provides fun outboxDao(db: MantisDatabase) = db.outboxDao()

    @Provides fun syncCursorDao(db: MantisDatabase) = db.syncCursorDao()

    @Provides fun modelRegistryDao(db: MantisDatabase) = db.modelRegistryDao()
}
