// :feature:categories - Manage categories (two levels, icons, colours, hide, reorder) and categorisation rules
plugins {
    alias(libs.plugins.mantis.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.feature.categories"
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    testImplementation(testFixtures(project(":core:domain")))
}
