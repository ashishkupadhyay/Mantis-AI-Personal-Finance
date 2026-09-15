// :feature:importwizard - CSV import wizard (named `importwizard` because `import` is a Java keyword)
plugins {
    alias(libs.plugins.mantis.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.feature.importwizard"
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(project(":core:domain")))
}
