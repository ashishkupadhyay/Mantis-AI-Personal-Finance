package io.github.ashishkupadhyay.mantis.core.data.repository

import io.github.ashishkupadhyay.mantis.core.common.result.AppError
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.database.seed.SampleDataSeeder
import io.github.ashishkupadhyay.mantis.core.datastore.SyncStateDataSource
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentGate
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Developer helpers; every entry point re-checks the [DevelopmentGate] so a release build can never seed data. */
@Singleton
class DefaultDevelopmentRepository @Inject constructor(
    gate: DevelopmentGate,
    private val seeder: SampleDataSeeder,
    private val syncState: SyncStateDataSource,
) : DevelopmentRepository {

    override val isDebugBuild: Boolean = gate.toolsEnabled

    override suspend fun seedSampleData(months: Int): Outcome<Int> {
        if (!isDebugBuild) return Outcome.failure(AppError.Unsupported("Sample data is only available in debug builds"))
        return Outcome.runCatching { seeder.seed(deviceId = syncState.deviceId(), months = months).transactions }
    }
}
