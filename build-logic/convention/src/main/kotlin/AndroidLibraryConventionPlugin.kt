import com.android.build.api.dsl.LibraryExtension
import io.github.ashishkupadhyay.mantis.buildlogic.configureKotlinAndroid
import io.github.ashishkupadhyay.mantis.buildlogic.mantisNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `mantis.android.library` — every `core:*` Android module, `widget`, `appfunctions`. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                namespace = mantisNamespace()
                configureKotlinAndroid(this)

                defaultConfig.apply {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }

                // Libraries only need the debug/release pair; keep build times down.
                testBuildType = "debug"
            }
        }
    }
}
