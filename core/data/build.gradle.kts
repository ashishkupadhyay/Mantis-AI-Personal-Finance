// :core:data - Repository implementations, entity<->model mappers, outbox writes
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:ml"))
    implementation(project(":core:llm"))
    implementation(project(":core:importer"))
    implementation(project(":core:receipts"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.paging.testing)
}
