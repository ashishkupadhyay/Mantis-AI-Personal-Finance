package io.github.ashishkupadhyay.mantis.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 37
internal const val MIN_SDK = 28

/**
 * Shared Android configuration for application and library modules. Kotlin compilation is
 * AGP 9's built-in Kotlin: no `kotlin-android` plugin, `jvmTarget` follows [CommonExtension.compileOptions].
 *
 * AGP 9's non-generic DSL interfaces expose nested blocks as properties (no lambda overloads),
 * hence the `.apply { }` style below instead of the script-accessor `block { }` syntax.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk {
            version = release(COMPILE_SDK)
        }

        defaultConfig.apply {
            minSdk = MIN_SDK
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        testOptions.unitTests.apply {
            isIncludeAndroidResources = true // Robolectric / Roborazzi
            isReturnDefaultValues = true
        }

        lint.apply {
            warningsAsErrors = true
            abortOnError = true
            checkDependencies = false
            xmlReport = true
            htmlReport = true
            // Version currency is Renovate's job; several pins are deliberate (ADR-0001 D1, D2, D13).
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        }
    }

    // Skeleton modules gain tests as their work packages land; an empty test set is not a failure (Gradle 9 default).
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }

    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        add("testImplementation", libs.findLibrary("turbine").get())
        add("testImplementation", libs.findLibrary("kotest-assertions").get())
    }
}
