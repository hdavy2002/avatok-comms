# AvaTok Comms

**GPL-3.0** · Android · built on the [Jami](https://jami.net) peer-to-peer engine.

A standalone Android app where two people add each other by QR code and can
then text-chat, voice-call, and video-call each other directly,
**peer-to-peer**. No phone number, no email, no central account, no AvaTok
server in the data path. Identity is a cryptographic keypair. Discovery is
the public Jami DHT.

## Architecture in one picture

```
┌──────────────────────────────────────────────────────────┐
│  AvaTok Comms (single APK, GPL-3.0)                        │
│                                                            │
│   ┌────────────────────────────────────────────────────┐  │
│   │  AvaTok UI  — OUR code, Kotlin + Jetpack Compose     │  │
│   │  contact list · chat · call · QR · our menus         │  │
│   └────────────────────────────────────────────────────┘  │
│                         │ calls                             │
│                         ▼                                   │
│   ┌────────────────────────────────────────────────────┐  │
│   │  libjamiclient  — Jami's Kotlin service layer        │  │
│   │  (account / contact / conversation / call) (vendored │  │
│   │  via git submodule, unmodified)                      │  │
│   └────────────────────────────────────────────────────┘  │
│                         │ JNI                               │
│                         ▼                                   │
│   ┌────────────────────────────────────────────────────┐  │
│   │  libjami daemon — C++, via NDK                       │  │
│   │  OpenDHT · PJSIP · GnuTLS · FFmpeg                    │  │
│   │  identity · NAT traversal · crypto · A/V calls        │  │
│   └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          │ network API (HTTP) — future, not in MVP
                          ▼
        ┌──────────────────────────────────────────┐
        │  AvaTok backend — separate closed repo,    │
        │  wallet · marketplace · payments · ledger  │
        │  GPL does NOT cross this network boundary  │
        └──────────────────────────────────────────┘
```

The top box is the **only** thing this repo contains as new code. The
middle and bottom boxes come from the upstream Jami project via a git
submodule at `vendor/jami-client-android/`. The backend will exist in a
fourth, separate closed repo — not yet built.

## License

**GPL-3.0-or-later.** Because the Jami engine links in-process via JNI,
the entire APK and its source must be GPL. This repo is public. The full
license text is in [`LICENSE`](LICENSE).

Attribution for Jami and its sub-dependencies is in
[`NOTICE`](NOTICE).

Why GPL is acceptable: the closed AvaTok backend (wallet, payments,
marketplace) lives in a **separate** repo and is reached only via
network API. GPL does not cross a network API boundary, so the
proprietary business logic stays protected. See
[`docs/licensing.md`](docs/licensing.md).

## Hard separation rule

This repository **must never** touch the existing `avatok.ai` Next.js
web app (`/Users/davy/Documents/websites/avatok/`). Different repos,
different toolchains, different deploys, never shared. See
[`docs/licensing.md`](docs/licensing.md) for the GPL-side reason and
[`docs/architecture.md`](docs/architecture.md) for the engineering
reason.

## Build

The native daemon side of this app has the same toolchain requirements
as upstream Jami (NDK 29, SWIG ≥ 4.2, CMake 4.1.2). Local Android-Studio
builds work on machines with ≥32 GB RAM; on 16 GB Macs the AGP packaging
step often deadlocks under memory pressure. The repo includes a GitHub
Actions workflow that produces a debug APK on a Linux runner — see
[`docs/build.md`](docs/build.md) and `.github/workflows/build.yml`.

## Project status

- ✅ Phase 0 — proposal approved (in the `avatok.ai` repo at
  `docs/proposals/avatok-comms-jami-mvp.md`).
- ✅ Phase 1 — Jami engine proven on two Android phones (text + photo
  + voice + video). One Jami stock-UI bug noted for later (asymmetric
  video due to spurious mute re-INVITEs — engine fine, UI bug we'll
  bypass when we build our call screen).
- ⏳ **Phase 2 — this repo's first deliverable.** AvaTok app skeleton
  on `libjamiclient`. Currently scaffolded; daemon-lifecycle wiring and
  identity-creation are the next commits.
- Phase 3 — QR pair + text chat
- Phase 4 — voice call screen
- Phase 5 — video call screen
- Phase 6 — strip Jami branding, AvaTok identity throughout

## Repository structure

```
avatok-comms/
  LICENSE                            GPL-3.0 full text
  NOTICE                             Jami + sub-dep attributions
  README.md                          this file
  .gitignore                         Android / Gradle / Kotlin
  docs/
    architecture.md                  detailed engine-on-UI design
    licensing.md                     GPL boundary explanation
    build.md                         how to build locally / via CI
  app/                               OUR Android app module
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/avatok/comms/  Compose UI + service layer wrapper
  vendor/
    jami-client-android/             git submodule, upstream Jami
                                     — we depend on its :libjamiclient
                                     module + daemon submodule via gradle
  .github/workflows/
    build.yml                        Cloud build (Linux runner) to APK
  settings.gradle.kts                Wires our :app to vendor's :libjamiclient
  build.gradle.kts                   Plugin versions, repositories
  gradle.properties                  JVM args, AGP flags
  gradle/, gradlew, gradlew.bat      Gradle wrapper
```

## Attribution

Built on Jami (https://jami.net), the GPL-3.0 P2P communication platform
by [Savoir-faire Linux](https://savoirfairelinux.com). This project is
not affiliated with the Jami project. See [NOTICE](NOTICE).
