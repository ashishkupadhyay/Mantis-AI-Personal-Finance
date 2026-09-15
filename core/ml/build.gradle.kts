// :core:ml - Normalizer, MerchantDictionary, RuleMatcher (RE2/J), LiteRT transaction + item classifiers, ModelManager
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.ml"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.litert)
    implementation(libs.re2j)
    implementation(libs.kotlinx.coroutines.core)
}

