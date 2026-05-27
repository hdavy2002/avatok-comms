// Top-level build.gradle.kts — AvaTok Comms
//
// Mirrors the structure of vendor/jami-client-android/jami-android/build.gradle.kts
// so we use the same plugin versions (via the libs.versions.toml from there).

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Hilt / KSP / Protobuf / Firebase plugins live in Jami's libs.versions.toml
    // — we may need them once we wire daemon lifecycle services in commit 2.
    // Adding `apply false` lines now would be premature; add per-module as needed.
}
