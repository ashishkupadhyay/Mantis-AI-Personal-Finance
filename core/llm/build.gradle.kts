// :core:llm - LlmProvider abstraction, BYOK adapters, KeyVault, UsageLedger, CapabilityProbe, JsonExtractor, ModelDownloader
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.llm"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}

