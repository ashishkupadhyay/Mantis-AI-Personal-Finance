package io.github.ashishkupadhyay.mantis.konsist

import java.io.File

/** Minimal reader of the Gradle module graph from `build.gradle.kts` files (no Gradle API needed in tests). */
internal object GradleGraph {

    val repoRoot: File = File(System.getProperty("mantis.repoRoot") ?: error("mantis.repoRoot not set"))

    data class Module(val path: String, val buildFile: File, val text: String) {
        val projectDependencies: Set<String> =
            Regex("""project\("(:[^"]+)"\)""").findAll(text).map { it.groupValues[1] }.toSet()
        val namespace: String? =
            Regex("""namespace\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
        val plugins: Set<String> =
            Regex("""alias\(libs\.plugins\.([a-zA-Z0-9.]+)\)""").findAll(text).map { it.groupValues[1] }.toSet()
        val isFeature get() = path.startsWith(":feature:")
        val isCore get() = path.startsWith(":core:")
    }

    /** Every module registered in settings.gradle.kts. */
    val modules: List<Module> by lazy {
        val settings = File(repoRoot, "settings.gradle.kts").readText()
        Regex("""include\("(:[^"]+)"\)""").findAll(settings).map { it.groupValues[1] }.map { path ->
            val dir = File(repoRoot, path.trimStart(':').replace(':', File.separatorChar))
            val buildFile = File(dir, "build.gradle.kts")
            Module(path, buildFile, buildFile.takeIf { it.exists() }?.readText() ?: "")
        }.toList()
    }

    fun module(path: String): Module = modules.first { it.path == path }
}
