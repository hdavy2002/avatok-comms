// app/build.gradle.kts — AvaTok Comms Android app
//
// First-commit scaffold. Daemon-lifecycle wiring and identity bootstrap
// come in commit 2. This commit just gets the Android project to a
// state where it can compile (a stub Activity) and is wired to
// :libjamiclient via project() dependency.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

    kotlinOptions {
        jvmTarget = "17"
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

    // Standard AndroidX + Compose stack (versions inherited from Jami's
    // libs.versions.toml via settings.gradle.kts versionCatalogs).
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM keeps Compose libraries in lockstep.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Kotlin coroutines & flow — for collecting libjamiclient state.
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
