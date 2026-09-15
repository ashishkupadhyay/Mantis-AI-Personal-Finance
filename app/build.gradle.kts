plugins {
    alias(libs.plugins.mantis.android.application)
    alias(libs.plugins.mantis.android.compose)
    alias(libs.plugins.mantis.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // Release-like build the `:benchmark` module drives (NFR-2/NFR-3): optimised, not debuggable, debug-signed so
        // it installs anywhere; `src/benchmark/res` flips the flag that unlocks seeding hooks.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applicationIdSuffix = ".benchmark"
        }
    }
}

roborazzi {
    // Shell screenshots (navigation suite at compact/expanded widths); CI verifies them like the design system's.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    // Every feature contributes its navigation entries through Hilt (EntryProviderInstaller set).
    implementation(project(":feature:accounts"))
    implementation(project(":feature:categories"))
    implementation(project(":feature:importwizard"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:transactions"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
