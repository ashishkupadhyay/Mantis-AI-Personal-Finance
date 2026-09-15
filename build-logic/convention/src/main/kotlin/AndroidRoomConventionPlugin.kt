import androidx.room3.gradle.RoomExtension
import io.github.ashishkupadhyay.mantis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `mantis.android.room` — Room 3 (`androidx.room3`: Kotlin-first, KSP-only, coroutine-first, driver-backed) with
 * exported schemas (NFR-13: every migration is tested). The bundled SQLite driver gives one SQLite build on every
 * API level, so FTS5 and query behaviour are identical from API 28 to 37 (ADR-0001 D16).
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room3")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-room-runtime").get())
                add("implementation", libs.findLibrary("androidx-room-paging").get())
                add("implementation", libs.findLibrary("androidx-sqlite-bundled").get())
                add("ksp", libs.findLibrary("androidx-room-compiler").get())
                add("testImplementation", libs.findLibrary("androidx-room-testing").get())
            }
        }
    }
}
