// :feature:onboarding - First run, local-only vs account, currency/month-start setup, App Lock enrolment
plugins {
    alias(libs.plugins.mantis.android.feature)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.feature.onboarding"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(testFixtures(project(":core:domain")))
}
