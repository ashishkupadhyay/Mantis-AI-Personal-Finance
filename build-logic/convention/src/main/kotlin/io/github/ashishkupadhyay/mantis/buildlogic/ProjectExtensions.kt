package io.github.ashishkupadhyay.mantis.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The shared `gradle/libs.versions.toml` catalog, usable from convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal const val BASE_NAMESPACE = "io.github.ashishkupadhyay.mantis"

/**
 * Derives a unique Android namespace from the Gradle path, e.g. `:core:designsystem` →
 * `io.github.ashishkupadhyay.mantis.core.designsystem`. Uniqueness is asserted by a Konsist test
 * because `android.uniquePackageNames` is disabled for LiteRT (ADR-0001 D4).
 */
internal fun Project.mantisNamespace(): String =
    BASE_NAMESPACE + path.replace(':', '.').replace('-', '_')
