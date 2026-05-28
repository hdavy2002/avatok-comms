// Top-level build.gradle.kts — AvaTok Comms
//
// Mirrors the structure of vendor/jami-client-android/jami-android/build.gradle.kts
// so we use the same plugin versions (via the libs.versions.toml from there).

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // google-services plugin must be declared here at the root so it's on
    // the classpath; the app module then conditionally applies it via
    // `apply(plugin = libs.plugins.google.services.get().pluginId)` only
    // for the withFirebase flavor build (gated on -PbuildFirebase=true).
    //
    // Without this `apply false` declaration at the root, the per-module
    // apply call fails with: "Plugin with id 'com.google.gms.google-services'
    // not found." That was the failure of CI run 26605272877.
    alias(libs.plugins.google.services) apply false
    // Hilt / KSP / Protobuf plugins live in Jami's libs.versions.toml
    // — we may need them once we wire daemon lifecycle services in commit 2.
    // Adding `apply false` lines now would be premature; add per-module as needed.
}
