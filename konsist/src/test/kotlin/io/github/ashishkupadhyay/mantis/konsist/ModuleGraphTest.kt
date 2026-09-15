package io.github.ashishkupadhyay.mantis.konsist

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Module dependency rules from doc 02 §3 (NFR-32). These read the Gradle build files, so they hold
 * regardless of what code exists yet. Convention-plugin-added dependencies are accounted for explicitly.
 */
class ModuleGraphTest {

    private val featureAllowed = setOf(
        ":core:common", ":core:model", ":core:domain", ":core:designsystem", ":core:ui", ":core:analytics", ":core:testing",
    )

    @Test
    fun `every module in settings has a build file`() {
        GradleGraph.modules.filter { it.text.isBlank() }.map { it.path }.shouldBeEmpty()
    }

    @Test
    fun `features depend only on the allowed core modules and never on each other`() {
        val violations = GradleGraph.modules.filter { it.isFeature }.flatMap { m ->
            (m.projectDependencies - featureAllowed).map { "${m.path} -> $it" }
        }
        withClue("feature modules may only depend on $featureAllowed") { violations.shouldBeEmpty() }
    }

    @Test
    fun `feature modules use the feature convention plugin`() {
        GradleGraph.modules.filter { it.isFeature && "mantis.android.feature" !in it.plugins }.map { it.path }.shouldBeEmpty()
    }

    @Test
    fun `pure JVM modules have no Android or Android-library dependencies`() {
        val jvm = setOf(":core:common", ":core:model", ":core:domain")
        val androidModules = GradleGraph.modules.filter { "mantis.jvm.library" !in it.plugins }.map { it.path }.toSet()
        jvm.forEach { path ->
            val m = GradleGraph.module(path)
            withClue("$path must stay pure JVM") {
                (m.projectDependencies intersect androidModules).shouldBeEmpty()
                m.text.contains("androidx.") shouldNotBe true
            }
        }
    }

    @Test
    fun `core domain depends only on model and common`() {
        (GradleGraph.module(":core:domain").projectDependencies - setOf(":core:model", ":core:common")).shouldBeEmpty()
    }

    @Test
    fun `core llm never depends on the Mantis network client`() {
        val forbidden = setOf(":core:network", ":core:data", ":core:database")
        (GradleGraph.module(":core:llm").projectDependencies intersect forbidden).shouldBeEmpty()
    }

    @Test
    fun `only core receipts may depend on ML Kit and only core ml on LiteRT`() {
        GradleGraph.modules.filter { it.path != ":core:receipts" && it.text.contains("mlkit") }.map { it.path }.shouldBeEmpty()
        GradleGraph.modules
            .filter { it.path != ":core:ml" && it.text.contains("libs.litert") }
            .map { it.path }
            .shouldBeEmpty()
    }

    @Test
    fun `only the appfunctions module may depend on androidx appfunctions`() {
        GradleGraph.modules
            .filter { !it.path.startsWith(":appfunctions") && it.text.contains("appfunctions") }
            .map { it.path }
            .shouldBeEmpty()
    }

    @Test
    fun `appfunctions depends only on domain-facing modules`() {
        val allowed = setOf(":core:common", ":core:model", ":core:domain", ":core:notifications")
        (GradleGraph.module(":appfunctions").projectDependencies - allowed).shouldBeEmpty()
    }

    @Test
    fun `android namespaces are explicit, unique and derived from the module path`() {
        // android.uniquePackageNames is disabled for LiteRT (ADR-0001 D4); enforce uniqueness for our own modules here.
        val android = GradleGraph.modules.filter { "mantis.jvm.library" !in it.plugins && it.path != ":app" }
        android.filter { it.namespace == null }.map { it.path }.shouldBeEmpty()
        val namespaces = android.mapNotNull { it.namespace }
        namespaces.toSet() shouldHaveSize namespaces.size
        android.forEach { m ->
            val expected = "io.github.ashishkupadhyay.mantis" + m.path.replace(':', '.').replace('-', '_')
            withClue("${m.path} namespace") { setOf(m.namespace) shouldContainAll setOf(expected) }
        }
    }
}
