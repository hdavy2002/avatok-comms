// app/build.gradle.kts — AvaTok Comms Android app
//
// First-commit scaffold. Daemon-lifecycle wiring and identity bootstrap
// come in commit 2. This commit just gets the Android project to a
// state where it can compile (a stub Activity) and is wired to
// :libjamiclient via project() dependency.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // NOTE: do NOT apply org.jetbrains.kotlin.android — AGP 9+ has built-in
    // Kotlin support and the standalone plugin is now a fatal error. Jami's
    // own jami-android/app keeps it via two compat flags in gradle.properties
    // (`android.builtInKotlin=true`, `android.newDsl=false`) — we don't need
    // those because we're starting fresh on AGP 9 conventions.
}

android {
    namespace = "com.avatok.comms"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.avatok.comms"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += properties["archs"]?.toString()?.split(",")
                ?: listOf("arm64-v8a")
            println("Building for ABIs $abiFilters")
        }

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // Don't ship full debug symbols in the APK — keeps it small
            // while still allowing logcat-level debugging.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        release {
            isMinifyEnabled = false  // Phase 6: enable R8, add proguard rules
            // Release signing is intentionally not configured yet — we
            // only ship debug APKs from CI until Phase 6.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9+ built-in Kotlin uses this DSL instead of the old kotlinOptions block.
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        viewBinding = false
        buildConfig = true
    }

    packaging {
        resources {
            // Common conflict-resolution rules when bundling many native
            // .so files via libjamiclient + the daemon.
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // The vendored Jami Kotlin service layer. THIS is the integration
    // point that gives our app access to the engine without us writing
    // any JNI directly.
    implementation(project(":libjamiclient"))

    // NOTE: we use explicit Gradle coordinates (not libs.* catalog entries)
    // for the Compose stack because Jami's vendored libs.versions.toml
    // doesn't declare Compose aliases — Jami's UI is View-based, not
    // Compose. When we later own our own libs.versions.toml (Phase 6 or
    // earlier when we stop sharing Jami's catalog), we'll move these
    // back to alias references.

    // Standard AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose BOM keeps the Compose libraries in lockstep
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Kotlin coroutines — for collecting libjamiclient state flows when
    // we wire the daemon in commit 3
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
