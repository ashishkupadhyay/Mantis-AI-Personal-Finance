package io.github.ashishkupadhyay.mantis.core.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.time.FixedClock
import io.github.ashishkupadhyay.mantis.core.database.seed.DefaultCategorySeeder
import io.github.ashishkupadhyay.mantis.core.database.seed.SampleDataSeeder
import java.time.Instant

/**
 * In-memory database on Robolectric's SQLite through the framework driver. Production uses the bundled driver;
 * both are plain `SQLiteDriver`s so the DAO code under test is identical.
 */
object TestDatabase {

    val clock = FixedClock(Instant.parse("2026-09-13T10:00:00Z"))
    val ids = UuidV7(nowMillis = { clock.epochMillis() })

    fun inMemory(): MantisDatabase =
        Room.inMemoryDatabaseBuilder<MantisDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(AndroidSQLiteDriver())
            .build()

    fun categorySeeder(db: MantisDatabase) = DefaultCategorySeeder(db, clock, ids)

    fun sampleSeeder(db: MantisDatabase) = SampleDataSeeder(db, clock, ids, categorySeeder(db))
}
