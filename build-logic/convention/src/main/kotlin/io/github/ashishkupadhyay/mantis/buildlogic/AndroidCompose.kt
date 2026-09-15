package io.github.ashishkupadhyay.mantis.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Compose wiring shared by the app, `core:designsystem`, `core:ui` and every feature module. */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-runtime").get())
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            // Scene-strategy metadata (ListDetailSceneStrategy.listPane()/detailPane()) and the navigation suite are
            // still experimental in material3-adaptive 1.3.0; every Compose module declares panes, so opt in once here.
            // The Material 3 *Expressive* opt-in is deliberately not here — it stays confined to core:designsystem.
            optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        includeSourceInformation.set(true)
        val reportsEnabled = providers.gradleProperty("mantis.composeCompilerReports").map { it.toBoolean() }.orElse(false)
        if (reportsEnabled.get()) {
            reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        }
    }
}
