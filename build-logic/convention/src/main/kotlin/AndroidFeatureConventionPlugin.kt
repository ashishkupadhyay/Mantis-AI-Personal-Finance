import io.github.ashishkupadhyay.mantis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `mantis.android.feature` — a `feature:*` module: Android library + Compose + Hilt, wired only to the
 * modules the dependency rules allow (doc 02 §3). Konsist enforces that features never reach
 * `core:data`/`core:database` or each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("mantis.android.library")
            pluginManager.apply("mantis.android.compose")
            pluginManager.apply("mantis.android.hilt")
            // Feature-internal NavKeys (nested settings, wizard steps) are @Serializable like the shared ones.
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                add("implementation", project(":core:common"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:analytics"))

                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get())
                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation3-runtime").get())
                // Scene metadata (ListDetailSceneStrategy.listPane()/detailPane()) is declared by the feature's entries.
                add("implementation", libs.findLibrary("androidx-material3-adaptive-navigation3").get())
                add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
                add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
                add("implementation", libs.findLibrary("androidx-paging-compose").get())
                add("implementation", libs.findLibrary("androidx-compose-material-icons-extended").get())

                add("testImplementation", libs.findLibrary("androidx-paging-testing").get())

                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
