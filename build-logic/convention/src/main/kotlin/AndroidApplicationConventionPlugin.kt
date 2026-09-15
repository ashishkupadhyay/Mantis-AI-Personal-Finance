import com.android.build.api.dsl.ApplicationExtension
import io.github.ashishkupadhyay.mantis.buildlogic.BASE_NAMESPACE
import io.github.ashishkupadhyay.mantis.buildlogic.TARGET_SDK
import io.github.ashishkupadhyay.mantis.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `mantis.android.application` — the single app module. Build separation per NFR-20j:
 * debug gets its own applicationId suffix; release runs R8 with resource shrinking.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                namespace = BASE_NAMESPACE
                configureKotlinAndroid(this)

                defaultConfig.apply {
                    applicationId = BASE_NAMESPACE
                    targetSdk = TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                buildTypes.getByName("debug").apply {
                    applicationIdSuffix = ".debug"
                    versionNameSuffix = "-debug"
                }
                buildTypes.getByName("release").apply {
                    optimization.enable = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }

                // Lint every module the app depends on in the app's context (minSdk-aware NewApi across JVM modules).
                lint.apply { checkDependencies = true }

                packaging.resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/LICENSE*",
                    "/META-INF/versions/**",
                )
            }
        }
    }
}
