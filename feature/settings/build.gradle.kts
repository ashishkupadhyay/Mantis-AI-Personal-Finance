// :feature:settings - Settings incl. AI & providers, privacy dashboard, security
plugins {
    alias(libs.plugins.mantis.android.feature)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.feature.settings"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(testFixtures(project(":core:domain")))
}
