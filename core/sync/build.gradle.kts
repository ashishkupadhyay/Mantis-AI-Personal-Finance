// :core:sync - SyncEngine, SyncWorker, HLC, conflict application, FCM hint receiver
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.sync"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.hilt.compiler)
}

