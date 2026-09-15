package io.github.ashishkupadhyay.mantis.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration harness (doc 02 §5.4, NFR-13). With a single schema version it proves the exported schema is on the
 * test classpath and creates cleanly; every future version adds a `migrateNToN+1` test here that seeds version N
 * with raw SQL, runs the migration and validates against the exported N+1 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchemaMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = MantisDatabase::class,
        driver = AndroidSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
    )

    @Test
    fun schemaV1CreatesFromTheExportedJson() = runTest {
        val connection = helper.createDatabase(MantisDatabase.VERSION)
        connection.execSQL("INSERT INTO sync_cursors (entityType, serverCursor, updatedAt) VALUES ('transactions', 'c1', 1)")
        connection.close()

        helper.runMigrationsAndValidate(MantisDatabase.VERSION, emptyList())
    }
}
