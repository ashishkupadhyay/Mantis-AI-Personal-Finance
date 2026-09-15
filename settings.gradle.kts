pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mantis"

// Enables typesafe project accessors (projects.core.model) in build scripts.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:ml")
include(":core:llm")
include(":core:receipts")
include(":core:importer")
include(":core:notifications")
include(":core:analytics")
include(":core:data")
include(":core:sync")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")
include(":feature:onboarding")
include(":feature:home")
include(":feature:transactions")
include(":feature:importwizard")
include(":feature:budgets")
include(":feature:categories")
include(":feature:insights")
include(":feature:recurring")
include(":feature:accounts")
include(":feature:assistant")
include(":feature:reports")
include(":feature:views")
include(":feature:settings")
include(":feature:receipts")
include(":widget")
include(":appfunctions")
include(":konsist")
include(":benchmark")

