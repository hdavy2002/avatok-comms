# AvaTok Comms — architecture

## The core idea

Reuse the Jami engine for everything hard (identity, NAT traversal,
crypto, messaging, A/V media pipeline). Write only our own UI on top
of Jami's Kotlin service layer (`libjamiclient`). Do NOT modify Jami's
engine internals.

## Layers

```
┌─────────────────────────────────────────────────────────────┐
│  app/  — OUR Kotlin + Jetpack Compose UI                      │
│         contacts · chat · call · QR · settings · about       │
└──────────────────────────────┬──────────────────────────────┘
                               │ method calls + state flows
┌──────────────────────────────▼──────────────────────────────┐
│  :libjamiclient  — Jami's Kotlin service layer (vendored)    │
│         AccountService · ContactService · ConversationService │
│         CallService · DeviceRuntimeService · others          │
└──────────────────────────────┬──────────────────────────────┘
                               │ JNI (SWIG-generated)
┌──────────────────────────────▼──────────────────────────────┐
│  libjami — C++ daemon (vendored as Jami's daemon submodule) │
│         OpenDHT · PJSIP · GnuTLS · FFmpeg                    │
└─────────────────────────────────────────────────────────────┘
```

## How our app drives the engine

1. **Daemon lifecycle.** On app start, an Android `Service` initialises
   `libjami` via `JamiService.startDaemon()`. The service stays alive
   while any account is active. When the user logs out OR the app is
   killed by the system, we call `JamiService.fini()`. (We follow the
   same pattern that `jami-android` uses — `JamiApplicationService`.)
2. **Identity.** On first launch, if no Jami account exists locally,
   we call `AccountService.addAccount(...)` with type `RING` to create
   a fresh keypair-based account. The Jami account ID is persisted by
   the daemon itself in `/data/data/com.avatok.comms/files/.../`.
3. **State observation.** `libjamiclient` services expose Kotlin
   `Flow<>`s for account state, contact list, conversation list, call
   state. Our Compose UI collects from these flows; we never read raw
   JNI directly.
4. **Actions.** User interactions call methods on the same services.
   Adding a contact, sending a message, placing a call — all go
   through `libjamiclient` and never touch JNI from our code.

## Repository layout

```
avatok-comms/
  LICENSE                       GPL-3.0
  NOTICE                        Third-party attributions
  README.md
  .gitignore

  docs/
    architecture.md             This file
    licensing.md                GPL boundary explanation
    build.md                    How to build (local + CI)

  app/                          OUR Android app module
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/avatok/comms/
        AvaTokApplication.kt    Application; daemon-lifecycle service init
        MainActivity.kt         Compose host
        ui/
          contacts/             Compose UI for contact list
          chat/                 (Phase 3) chat screen
          call/                 (Phase 4/5) call screens
          settings/             (Phase 6) AvaTok settings
        services/
          DaemonService.kt      (Phase 2 commit 2) wraps Jami daemon lifecycle
          IdentityBootstrap.kt  (Phase 2 commit 2) first-launch account create

  vendor/
    jami-client-android/        Git submodule pointing at upstream
                                (review.jami.net master, pinned by commit)
                                We depend on its :libjamiclient module
                                and its daemon submodule for native build.

  .github/workflows/
    build.yml                   Cloud build → APK as GH Release

  settings.gradle.kts           includeBuild("vendor/jami-client-android/jami-android")
                                or explicit `include(":libjamiclient")` with
                                projectDir override
  build.gradle.kts              Root plugins / repositories
  gradle.properties             AGP flags + JVM args + workarounds
  gradle/, gradlew*             Wrapper (pinned to gradle 9.3.1 stable)
```

## Versions pinned

Same as Jami's docker/Dockerfile (since we link in-process):

- JDK 17 (Temurin)
- Gradle 9.3.1 (NOT 9.4.0-rc-1 which deadlocks — see jami-build-runner)
- AGP 9.1.0 (matches Jami's libs.versions.toml)
- Kotlin 2.3.10
- NDK 29.0.14206865
- CMake 4.1.2
- SWIG ≥ 4.2.1 (built from source in CI)
- minSdk = 31 (Android 12+), targetSdk = 36, compileSdk = 36
- arm64-v8a only (matches device floor)

## Patches we carry against upstream Jami

These are encoded in `.github/workflows/build.yml` (applied to the
submodule's working tree before each build, NOT committed back into the
submodule). See [`docs/build.md`](build.md) for the rationale per patch.

1. Gradle wrapper 9.4.0-rc-1 → 9.3.1 stable (worker pool deadlock)
2. `daemon/bin/jni/make-swig.sh` add `-module JamiService` (SWIG 4.4 strictness)
3. `make .yaml-cpp` pre-build (Jami's gradle graph for Android doesn't
   trigger this contrib)
4. `app/src/main/res/values-nn_NO` rename to `values-nn-rNO` (AGP 9 strict
   resource qualifier validation)

## What this repo will NOT do

- Modify Jami daemon C++ source
- Fork Jami's UI (`jami-android/app`) — we have our own at `app/`
- Use Jami's branding, name, or icon in our UI
- Implement group chat, payments, wallet, or anything outside the
  MVP scope (see proposal §4.6)
- Touch the `avatok.ai` Next.js web app repo. Ever.
