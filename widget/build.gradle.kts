// :widget - Glance widgets (month at a glance, quick add)
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.compose)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.widget"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
}

