# Bloviate

Bloviate is a Shippopotamus Labs floating-bubble dictation app for Android: a bubble appears over any focused text field, you tap and speak, and cleaned text is inserted at the cursor. Transcription runs **on-device** with whisper.cpp via the shared [`whisper-android`](https://gitlab.com/browntown.development-group/whisper-android) library — no audio leaves the phone, no account, no subscription. This repo is currently a Gradle skeleton (ENC-57); the accessibility watcher, bubble overlay, and voice loop land in later Phase 1/2 tickets.

## Build

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

Requirements: JDK 17 (Gradle daemon runs on a JDK 21 toolchain, as in Enclave), Android SDK with NDK **28.2.13676358** installed (the whisper-android native build pins it).

### Composite build notes

The app consumes `whisper-android` through a Gradle composite build: the library is vendored as a submodule at `third_party/whisper-android`, and `settings.gradle.kts` substitutes the `com.shippopotamuslabs:whisper-android` / `whisper-android-hilt` Maven coordinates with the included build's projects.

Gotcha (learned on Enclave, 2026-10-18): the included build resolves the Android SDK location **per build**. If you build from an IDE or shell without `ANDROID_HOME` in the environment, Gradle reads `sdk.dir` from a `local.properties` file — and it looks for one inside `third_party/whisper-android/` as well as at this repo's root. Copy yours over:

```bash
cp local.properties third_party/whisper-android/local.properties
```

`local.properties` is git-ignored in both repos, so this never gets committed.
