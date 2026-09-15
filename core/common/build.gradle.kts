// :core:common - Money, Outcome, DispatcherProvider, Clock, ids, Logger facade, FeatureFlags, SignedAssetVerifier
plugins {
    alias(libs.plugins.mantis.jvm.library)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.eddsa)
}
