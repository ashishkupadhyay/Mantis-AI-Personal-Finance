// :core:importer - CSV sniffing (encoding, delimiter, header row), streaming RFC 4180 parsing, bank presets, column
// mapping, date/amount parsing. Pure JVM: the database half of an import lives in core:data (ImportRepository).
plugins {
    alias(libs.plugins.mantis.jvm.library)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
