// :core:notifications - Channels, budget alert poster, snooze action, deep links into the app
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.notifications"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
