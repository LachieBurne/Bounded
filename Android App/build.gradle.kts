// Versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself. Declaring the Kotlin plugin here pins the Kotlin version it uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}
