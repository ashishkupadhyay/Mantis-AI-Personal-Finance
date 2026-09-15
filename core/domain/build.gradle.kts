// :core:domain - Use-cases, repository interfaces, CategorizationPipeline, BudgetEngine, InsightEngine, RecurringDetector, PaceForecaster, AssistantOrchestrator, ReportAggregator
plugins {
    alias(libs.plugins.mantis.jvm.library)
    // In-memory fakes of the repository contracts, shared with feature ViewModel tests via testFixtures(project(":core:domain")).
    `java-test-fixtures`
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    // Linear-time regex for user rules (NFR-20b).
    implementation(libs.re2j)
    // PagingData is part of the TransactionRepository contract; paging-common is pure JVM/KMP.
    api(libs.androidx.paging.common)

    testFixturesImplementation(project(":core:common"))
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
