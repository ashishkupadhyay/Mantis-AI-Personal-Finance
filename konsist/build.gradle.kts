// :konsist - architecture tests (module dependency rules, namespaces, code conventions). Runs in CI on every PR.
plugins {
    alias(libs.plugins.mantis.jvm.library)
}

dependencies {
    testImplementation(libs.konsist)
}

tasks.withType<Test>().configureEach {
    // Konsist and the Gradle-graph test read the repository from disk; make the root explicit.
    systemProperty("mantis.repoRoot", rootProject.projectDir.absolutePath)
}
