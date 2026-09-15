// :core:datastore - typed DataStore files (UserPreferences, SyncState) and the Keystore-encrypted tokens / LLM vault
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.datastore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.core)
    // Proto wire format via kotlinx-serialization (@ProtoNumber-tagged fields) — no protoc/Gradle plugin needed.
    implementation(libs.kotlinx.serialization.protobuf)

    testImplementation(project(":core:testing"))
}
