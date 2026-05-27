# AvaTok Comms — build

## TL;DR

```sh
# Initialise the Jami submodule (one-time, after fresh clone)
git submodule update --init --recursive

# Build via cloud (Linux runner; recommended)
git push origin main   # triggers .github/workflows/build.yml
                       # download APK from the workflow's GH Release

# Build locally (NEEDS ≥32 GB RAM, full Jami toolchain — fiddly)
# See "Local build" section below.
```

## Why cloud builds are the default

The Jami native daemon is heavyweight to compile (FFmpeg, PJSIP,
OpenDHT, GnuTLS, libgmp, libnettle, opus, speex, libvpx, x264,
dhtnet, libgit2, ... — all cross-compiled to Android via the NDK).
The combined AGP + Kotlin daemon + AAPT2 + R8 + D8 packaging chain
needs ~5-6 GB of free RAM. On a 16 GB Mac with normal background apps
running (Chrome, Slack, Docker/Colima) it often deadlocks under memory
pressure rather than failing cleanly.

GitHub-hosted Linux runners (4 cores, 16 GB RAM, no GUI) have far less
OS overhead and the build completes reliably in ~25–35 minutes. So our
default path is: push → CI builds → download APK from GH Releases.

The cloud build is configured in `.github/workflows/build.yml`. It
mirrors the approach we proved in `github.com/hdavy2002/jami-build-runner`
(which built stock Jami) but builds our `:app` module instead of
Jami's `:app` module.

## Cloud build details

### Workflow flow

1. Checkout `avatok-comms` + the Jami submodule recursively.
2. Install Linux toolchain: SWIG ≥ 4.2.1 (built from source), JDK 17,
   NDK 29.0.14206865, CMake 4.1.2, autotools/yasm/nasm/etc.
3. Apply our **build-time patches** to the Jami submodule's working
   tree (these are NOT committed back to the submodule):
   - `vendor/jami-client-android/jami-android/gradle/wrapper/gradle-wrapper.properties`
     → downgrade `gradle-9.4.0-rc-1` to `gradle-9.3.1` (avoid worker
     deadlock).
   - `vendor/jami-client-android/jami-android/gradle.properties`
     → if `vfork` is present, switch to `posix_spawn` (Linux runners
     don't strictly need this, but it's safe to do).
   - `vendor/jami-client-android/daemon/bin/jni/make-swig.sh`
     → add `-module JamiService` to the swig invocation (SWIG 4.4
     strictness).
   - `vendor/jami-client-android/jami-android/app/src/main/res/values-nn_NO/`
     → rename to `values-nn-rNO/` (AGP 9 strict resource qualifier).
4. Bootstrap Jami's daemon-tools (m4, libtool, autoconf, automake,
   pkg-config) via `vendor/jami-client-android/daemon/extras/tools`.
5. Pre-generate JNI bindings via `make-swig.sh` (so our Kotlin compile
   sees `net.jami.daemon.JamiService` from the first parse).
6. First gradle pass against our `:app` — expected to fail at
   `find_package(yaml-cpp)`. This pass creates the contrib build dir.
7. Explicitly build yaml-cpp: `make .yaml-cpp` in
   `vendor/jami-client-android/daemon/contrib/build-aarch64-linux-android`.
8. Real gradle pass: `./gradlew :app:assembleDebug -Parchs=arm64-v8a`.
9. Upload APK as workflow artifact AND publish as a GitHub Release
   (tagged `build-N-jami-<short-sha>`) so it's directly downloadable.

### Why "two gradle passes"

Jami's contrib build system has a missing edge in its gradle/CMake
dependency graph for Android cross-compile: `find_package(yaml-cpp)`
is required by `daemon/CMakeLists.txt:637`, but the gradle invocation
doesn't trigger building yaml-cpp before that find_package runs. The
two-pass approach lets the first pass set up the contrib build
directory, then we manually build yaml-cpp, then the second pass
succeeds.

This pattern is documented in detail in
`avatok.ai/docs/sessions/2026-05-26-avatok-comms-jami-phase0-phase1.md`
(the session journal where we discovered the issue) and is the same
trick used by `github.com/hdavy2002/jami-build-runner`.

## Local build (optional — for development with adequate hardware)

Only do this if you have **≥32 GB RAM** and a willingness to debug
gnarly toolchain issues. Otherwise use the cloud build.

### Prerequisites

- macOS or Linux. **Windows is not supported** — Jami's contrib build
  doesn't work on native Windows. WSL2 is theoretically possible but
  not tested for this repo.
- Android Studio installed, with NDK 29.0.14206865 and CMake 4.1.2
  available through SDK Manager.
- JDK 17 (e.g. `brew install openjdk@17` on macOS).
- SWIG ≥ 4.2 (`brew install swig` on macOS; if your distro's package
  is older, build from source — see Jami's docker/Dockerfile).
- GNU autotools, libtool, pkg-config, yasm, nasm, ninja-build, cmake,
  texinfo.
- On macOS: `ln -sf /opt/homebrew/bin/glibtoolize /opt/homebrew/bin/libtoolize`
  (brew's libtool installs with a `g` prefix; Jami's autoreconf wants
  the un-prefixed name).
- On macOS 26+: ensure Jami's `gradle.properties` uses `posix_spawn`,
  not `vfork`, for `jdk.lang.Process.launchMechanism`.

### Build

```sh
git submodule update --init --recursive

# Apply the build patches to the submodule's working tree
# (the cloud workflow does this automatically — locally you do it once)
cd vendor/jami-client-android
sed -i.bak 's|gradle-9.4.0-rc-1-bin\.zip|gradle-9.3.1-bin.zip|' \
  jami-android/gradle/wrapper/gradle-wrapper.properties
sed -i.bak 's|^swig -v -c++ -java \\$|swig -v -c++ -java -module JamiService \\|' \
  daemon/bin/jni/make-swig.sh
[ -d jami-android/app/src/main/res/values-nn_NO ] && \
  mv jami-android/app/src/main/res/values-nn_NO jami-android/app/src/main/res/values-nn-rNO

# Bootstrap Jami daemon tools
( cd daemon/extras/tools && ./bootstrap && make -j 1 && \
  export PATH="$(pwd)/build/bin:$PATH" && make -j 1 )
export PATH="$(pwd)/daemon/extras/tools/build/bin:$PATH"

# Pre-generate JNI bindings
( cd daemon/bin/jni && \
  PACKAGEDIR=$(pwd)/../../../jami-android/libjamiclient/src/main/java \
  ./make-swig.sh )

cd ../..  # back to avatok-comms root

# First gradle pass (will fail at yaml-cpp — expected)
./gradlew :app:assembleDebug -Parchs=arm64-v8a || true

# Build yaml-cpp explicitly
( cd vendor/jami-client-android/daemon/contrib/build-aarch64-linux-android && \
  make .yaml-cpp )

# Real build
./gradlew :app:assembleDebug -Parchs=arm64-v8a

# APK
ls app/build/outputs/apk/debug/*.apk
```

If anything goes wrong, consult
`avatok.ai/docs/sessions/2026-05-26-avatok-comms-jami-phase0-phase1.md`
— that file documents every Jami-on-macOS-26 gotcha we've hit, with
fixes.

## Updating the Jami submodule

The submodule pin lives in this repo. To upgrade to a newer Jami:

```sh
cd vendor/jami-client-android
git fetch origin
git checkout <new-commit-or-tag>
cd ../..
git add vendor/jami-client-android
git commit -m "Bump jami-client-android to <ref>"
```

After bumping, run a cloud build to verify nothing broke. If a patch
no longer applies cleanly (because Jami upstreamed our fix), the
workflow's sed steps are idempotent and will skip — but the gradle
build itself is the real signal.

## Patch hygiene

We deliberately do NOT commit our patches into the Jami submodule's
git history — that would create a fork we have to maintain. Instead,
patches live in `.github/workflows/build.yml` as inline `sed` /
`mv` commands that run against the submodule's working tree at build
time. Each patch is documented with WHY it exists. If Jami upstream
fixes any of them, we delete the corresponding step.

When Phase 1 was discovering these patches, we had a separate
`jami-build-runner` repo that built **stock** Jami with the same
patches. That repo and this one share the same patch set; if a patch
breaks in one, it breaks in the other.

## Cost

Public repo on GitHub: unlimited free Actions minutes for builds.

First build is ~30 minutes; subsequent builds with warm caches ~10
minutes. The full SWIG-from-source step takes ~30 seconds and is
cached after the first build via `actions/cache`.
