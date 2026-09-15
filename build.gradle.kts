// Top-level build file. Module configuration lives in build-logic/convention; keep this file to plugin
// declarations (apply false) and repository-wide tooling (Detekt, Kover).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// ---- Detekt: one config for every module, Compose rules included ----
val detektConfig = files("$rootDir/config/detekt/detekt.yml")

subprojects {
    if (path == ":build-logic") return@subprojects
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(detektConfig)
        basePath = rootDir.absolutePath
        source.setFrom("src/main/kotlin", "src/main/java", "src/test/kotlin", "src/androidTest/kotlin")
    }
    dependencies {
        add("detektPlugins", rootProject.libs.detekt.compose.rules)
    }
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}

// ---- Kover: merged coverage report across modules (NFR-33) ----
// Every measured module must apply the plugin so it exposes a `kover` variant for the root aggregation.
val koverModules = subprojects.filter { it.path != ":konsist" && it.path != ":build-logic" && it.path != ":core:testing" }
koverModules.forEach { it.apply(plugin = "org.jetbrains.kotlinx.kover") }
dependencies {
    koverModules.forEach { kover(it) }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_Factory", "*_Factory\$*", "*_MembersInjector", "*_HiltModules*", "*Hilt_*", "*_Impl", "*_Impl\$*",
                    "*Dagger*", "*ComposableSingletons*", "*BuildConfig", "*.databinding.*", "*_Provide*Factory*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview", "dagger.internal.DaggerGenerated")
                packages("*.spike")
            }
        }
        total {
            html { onCheck.set(false) }
            xml { onCheck.set(false) }
        }
    }
}
