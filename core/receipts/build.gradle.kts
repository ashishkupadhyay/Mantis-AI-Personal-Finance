// :core:receipts - Document scanner wrapper, OCR, ReceiptParser, ItemCategorizer, ReceiptSelfCheck, ReceiptMatcher, ReceiptAiRepair
plugins {
    alias(libs.plugins.mantis.android.library)
    alias(libs.plugins.mantis.android.hilt)
}

android {
    namespace = "io.github.ashishkupadhyay.mantis.core.receipts"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ml"))
    implementation(project(":core:llm"))
    implementation(libs.kotlinx.coroutines.core)
}

