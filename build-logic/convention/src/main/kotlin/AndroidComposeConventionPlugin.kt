import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import io.github.ashishkupadhyay.mantis.buildlogic.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `mantis.android.compose` — adds the Compose compiler plugin and the shared Compose dependencies. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { configureAndroidCompose(this) }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { configureAndroidCompose(this) }
            }
        }
    }
}
