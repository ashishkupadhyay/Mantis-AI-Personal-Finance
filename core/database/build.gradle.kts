// :core:database - Room 3 DB, entities, DAOs, migrations, FTS, sample-data seeder
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
    alias(libs.plugins.mantis.android.room)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.database"
}

// The Room plugin exposes exported schemas to instrumented tests only; our migration tests run on Robolectric,
// so the schema directory is also added to the unit-test assets through the variant API (AGP 9 new DSL).
androidComponents {
    onVariants { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.sources
            ?.assets
            ?.addStaticSourceDirectory("schemas")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.paging.runtime)

    testImplementation(project(":core:testing"))
    // Unit tests run on Robolectric's SQLite through the framework driver; production uses the bundled driver.
    testImplementation(libs.androidx.sqlite.framework)
}
