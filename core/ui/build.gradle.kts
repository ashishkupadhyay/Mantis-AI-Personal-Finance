// :core:ui - Shared UI (MoneyText, TransactionRow, CategoryChip, …) and the navigation contracts every feature uses
// (MantisKey, MantisNavigator, EntryProviderInstaller, per-tab back-stack state).
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.ui"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)

    // Navigation contracts: keys are serializable NavKeys; the state uses saved-state serializers and the
    // view-model-store decorator so every entry owns its ViewModels.
    api(libs.androidx.navigation3.runtime)
    // Scene/OverlayScene for the bottom-sheet strategy shared by every feature that opens a sheet destination.
    api(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.savedstate.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
}
