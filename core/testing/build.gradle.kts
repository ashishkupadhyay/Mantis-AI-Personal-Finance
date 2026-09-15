// :core:testing - Fakes, test rules, Roborazzi helpers, sample data
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.compose)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.testing"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    // Consumers without the Compose convention (core:database, core:datastore) still need the BOM for ui-test-junit4.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.datastore)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.test.core.ktx)
    api(libs.hilt.android.testing)
}

