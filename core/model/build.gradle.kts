// :core:model - Pure Kotlin domain models (no Android deps); Money and ids come from :core:common
plugins {
    alias(libs.plugins.mantis.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:common"))
    // UserPreferences is both the domain model and the proto wire shape of its DataStore file (ADR-0001 D17).
    api(libs.kotlinx.serialization.protobuf)
}
