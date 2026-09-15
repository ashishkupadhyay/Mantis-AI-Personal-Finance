// :core:designsystem - MantisTheme (Material 3 Expressive), tokens, wrapped components, charts, motion, previews
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.designsystem"
}

roborazzi {
    // Baselines live with the module; CI runs verifyRoborazziDebug and fails on diffs (doc 05 §10).
    outputDir.set(file("src/test/screenshots"))
}

kotlin {
    compilerOptions {
        // The Expressive opt-in is confined to this module (doc 05 §3); features consume wrappers only.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.adaptive.navigation.suite)
    api(libs.androidx.material3.adaptive)
    api(libs.androidx.material3.adaptive.layout)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.materialkolor.utilities)
    api(libs.kotlinx.collections.immutable)
    implementation(project(":core:common"))

    testImplementation(project(":core:testing"))
}
