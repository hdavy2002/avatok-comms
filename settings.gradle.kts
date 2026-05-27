// settings.gradle.kts — AvaTok Comms
//
// Includes our own :app module AND the :libjamiclient module from the
// vendored Jami submodule. We override its project path so we can refer
// to it as a normal gradle subproject without forking Jami's settings.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    versionCatalogs {
        // Reuse Jami's libs.versions.toml so we get the same pinned
        // versions of AGP, Kotlin, AndroidX, Compose, etc.
        // — keeps our app and libjamiclient on identical dependency
        // graphs and avoids classpath conflicts.
        create("libs") {
            from(files("vendor/jami-client-android/jami-android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "avatok-comms"

include(":app")

// :libjamiclient lives inside the Jami submodule at
//   vendor/jami-client-android/jami-android/libjamiclient
// We mount it as a subproject so :app can depend on it via project(":libjamiclient").
include(":libjamiclient")
project(":libjamiclient").projectDir =
    file("vendor/jami-client-android/jami-android/libjamiclient")
