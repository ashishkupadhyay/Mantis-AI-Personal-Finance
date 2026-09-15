import io.github.ashishkupadhyay.mantis.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * `mantis.jvm.library` — pure Kotlin modules with no Android dependency (`core:model`, `core:domain`,
 * `core:common`, `konsist`): fast JUnit 5 tests, future KMP path.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            // Standalone Android Lint so NewApi (minSdk 28) and friends run on pure-JVM modules too — java.time
            // has Java 9+ members (LocalDate.ofInstant) that only exist on Android 34+.
            pluginManager.apply("com.android.lint")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    allWarningsAsErrors.set(true)
                }
            }
            // Production code compiles against the Java 8 API surface (like javac --release 8): Android's java.* on
            // minSdk 28 has no Java 9+ members (LocalDate.ofInstant crashed at runtime on API 28) and Android Lint
            // cannot check JVM modules. -Xjdk-release must equal the jvm target, so main is 1.8 bytecode; tests stay
            // on 17 (JUnit 6 needs it) and the module's Gradle attributes stay 17.
            tasks.named<KotlinCompile>("compileKotlin") {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                    freeCompilerArgs.add("-Xjdk-release=8")
                }
            }
            tasks.named<JavaCompile>("compileJava") {
                sourceCompatibility = JavaVersion.VERSION_1_8.toString()
                targetCompatibility = JavaVersion.VERSION_1_8.toString()
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                failOnNoDiscoveredTests.set(false)
            }

            extensions.configure<com.android.build.api.dsl.Lint> {
                warningsAsErrors = true
                abortOnError = true
                xmlReport = true
                htmlReport = true
                // TrulyRandom targets Android ≤ 4.3 (minSdk is 28); version currency is Renovate's job.
                disable += setOf("GradleDependency", "NewerVersionAvailable", "TrulyRandom")
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit5-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit5-platform-launcher").get())
                add("testImplementation", libs.findLibrary("kotest-assertions").get())
                add("testImplementation", libs.findLibrary("kotest-property").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libs.findLibrary("turbine").get())
            }
        }
    }
}
