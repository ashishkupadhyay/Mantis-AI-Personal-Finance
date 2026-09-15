// :feature:accounts - Accounts list (carousel + list), account detail, account editor
plugins {
    alias(libs.plugins.mantis.android.feature)
    alias(libs.plugins.roborazzi)
}

roborazzi {
    // Screen-level baselines (light / dark / 200 % font) live with the feature; CI runs verifyRoborazziDebug.
    outputDir.set(file("src/test/screenshots"))
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.feature.accounts"
}

dependencies {
    testImplementation(testFixtures(project(":core:domain")))
}
