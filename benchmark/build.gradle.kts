// :benchmark - Macrobenchmarks against the app's `benchmark` build type (NFR-2 list scroll, NFR-3 search). Device only:
//   gradlew :benchmark:connectedBenchmarkAndroidTest
// Results land in benchmark/build/outputs/connected_android_test_additional_output.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Mirrors the app's `benchmark` build type; the test APK itself stays debuggable as Macrobenchmark requires.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
