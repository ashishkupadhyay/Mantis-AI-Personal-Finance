// :core:analytics - Opt-in analytics facade (no-op by default)
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.analytics"
}

dependencies {
    implementation(project(":core:common"))
}

