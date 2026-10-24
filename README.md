# Bloviate

Bloviate is a dictation app for Android from Shippopotamus Labs. A bubble sits over the text field you are typing in. You tap the bubble, talk, tap again, and your words land at the cursor. Pick a style and the words get rewritten in that voice on the way in: your phrasing out, Cicero's phrasing in, same facts.

Transcription runs on the phone. Audio stays on the device. Styling runs through a Firebase Function that calls a language model, and that part is a subscription.

## How a dictation happens

1. An accessibility service watches for focused text fields and tells the bubble when to show.
2. The bubble lives in a foreground service with an overlay window. It draws over other apps and takes the tap.
3. The phone records, transcribes on device, and runs the transcript through on-device cleanup: a baseline post-processor, your personal dictionary, casing rules.
4. If you picked a cloud style and hold a subscription, the cleaned transcript goes to the `styleText` Firebase Function. The function checks your entitlement, builds the style prompt, calls the model chain, checks the output, and returns styled text.
5. The app puts the finished text into the field at the cursor.

If styling fails or the subscription check says no, the bubble tells you and the raw transcript stays yours to use. Dictation itself never waits on the network.

## Dictation engines

Two engines ship in the app. A settings pick chooses one, and the app falls back to the other while the picked engine's model is still downloading.

- Parakeet TDT 0.6B v3, INT8 ONNX, running on sherpa-onnx. The default. It covers 25 European languages and keeps proper nouns intact better in our testing. The model download is about 640 MiB.
- Whisper, the base.en model from whisper.cpp, through the shared whisper-android library. It covers 99 languages and serves as the fallback.

Models download from Hugging Face on first use and live in app storage after that. The engine rule sits in one place, `EffectiveEngineResolver` in `:core:domain`, and every screen reads from it.

## Styles

A style is a writing voice with a written instruction set, worked examples, and rules for what it may change. Every style obeys one law across the whole catalog: facts, names, numbers, dates, and quoted text stay as dictated. Style changes the telling.

Two kinds of styles exist:

- Mechanical styles run on the phone as plain string transforms. They cost nothing and need no account. The catalog has two: `normal-caps-casual`, the default, and `lowercase-casual`.
- Cloud styles run on a language model through the Firebase Function. The server catalog holds 32 of them, in six sections:

| Section | Count | Styles |
| --- | ---: | --- |
| Serious | 3 | Polish, Professional, Essayist. Polish edits lightly. Professional rewrites into finished business prose. Essayist writes the long form. |
| Ancient Philosophers | 8 | Cicero, Aristotle, Seneca, Marcus Aurelius, Socrates, Plato, Aquinas, Augustine |
| Writers | 4 | Shakespeare, Hemingway, Dickens, Oscar Wilde |
| Eras and Genres | 4 | Noir Detective, Pirate, Talkie, The Colonel |
| Personas | 5 | Attenborough, Drill Sergeant, Town Crier, Belligerent, Genius |
| Modern Parody | 8 | Corporate LinkedIn, Ultra HR, Silicon Valley Bro, Legal Contract, Academic Jargon, Gen Z Brainrot, Edgelord, AI Psychosis |

Serious styles play by a stricter rule: they add no facts of their own. The other sections may invent color, framing, and flourish. They still may never contradict what you said or present invented facts as yours.

Custom styles are yours. Describe a voice in the app and the server generates the style definition for it. Generation runs 20 per day and 100 per month at most. Custom styles live in your account in Firestore, keyed to Google sign-in. The Styles screen shows your customs first, then the sections above.

Style definitions exist twice on purpose. Typed Kotlin definitions live in `:core:domain` and drive the app UI and the on-device prompt builder. A TypeScript twin in `functions/src/style/styleCatalog.ts` drives the server. Tests pin the two to the same wording where they must match.

## Accounts and the subscription

On-device dictation works signed out. It is free and carries no usage limit.

Cloud styles need two things: a Google sign-in and a subscription. The subscription costs $2.99 per month with a one-week free trial, bought through Google Play. The server checks entitlement in Firestore before any model call, reading `users/{uid}/entitlement/play` for a Play-backed record or `users/{uid}/entitlement/override` for complimentary access. Clients can read those records. Only the backend writes them.

Cloud styling carries a monthly fair-use cap, counted in normalized tokens across both models in the chain. One dictation can run as long as you keep talking. The cap counts the month's total volume.

## The model chain

The `styleText` function sends each request through a chain, in this order:

| Step | Model | Routing |
| ---: | --- | --- |
| 1 | DeepSeek V4.1 Flash | OpenRouter across a pinned provider list (Inference.net, Together, Baseten, Fireworks, Crusoe, DeepInfra, FP8 builds first) |
| 2 | Gemini 3.1 Flash-Lite | OpenRouter, pinned to Google Vertex providers and sorted by latency. Runs when the primary chain fails or its output fails the guard. |

Returned text passes an output guard and a response cleaner before the app sees it. If the final output still fails the guard, the app gets your cleaned transcript back. Every attempt logs a structured record with token counts and an estimated cost, and styling outcomes report to Crashlytics from the app.

API keys live server side in Firebase secrets. The app holds no model key.

## Architecture

The Android app is four Gradle modules plus a composite build. Dependencies point one way: app to application to domain, data implements the ports.

| Module | What it holds |
| --- | --- |
| `:app` | The Android entry points: the accessibility field watcher, the bubble overlay service, the boot receiver, Compose screens, and Hilt wiring |
| `:core:application` | Use cases and the ports they consume: transcription, styling, model stores, dictionary, auth session, preferences |
| `:core:domain` | The rules with no framework in them: style definitions, the dictation pipeline evaluators, the personal dictionary, engine resolution, bubble geometry and gesture math |
| `:core:data` | The implementations: the two transcription engines, Firebase gateways for styling and custom styles, model downloaders and stores, DataStore preferences |
| `third_party/whisper-android` | A git submodule consumed as a Gradle composite build. It carries the whisper.cpp engine and its own nested whisper.cpp submodule, so clones need two levels of submodules |
| `functions/` | A separate TypeScript codebase, Node 22, deployed as Firebase Functions. It holds the callable endpoints: `styleText`, `saveCustomStyle`, `listCustomStyles`, `deleteCustomStyle`, `generateCustomStyle` |

House rules for the codebase live in `contracts/`: architecture, code style, data layer, transcription, unit testing, UI design, and the voice contract for user-facing copy. `AGENTS.md` at the root routes coding work to the right contract. Read those before changing behavior; they state requirements, and pull requests get checked against them.

## Tech stack

- Kotlin 2.3.21, Android Gradle Plugin 9.4.1, Gradle 9.8.1.
- Jetpack Compose with Material 3 (Compose BOM 2026.09.00), Hilt 2.59.2 for dependency injection.
- Firebase BOM 34.19.0: Auth, Firestore, Functions, Crashlytics.
- sherpa-onnx 1.13.8, vendored as an AAR in `core/data/libs/` with its source and checksum recorded there. It runs the Parakeet model.
- whisper.cpp through the whisper-android composite build, for the Whisper engine.
- TypeScript on Node 22 for `functions/`, tested with the Node test runner.
- JUnit 5, Robolectric 4.16.1, Turbine, and hand-written fakes for Android tests. Detekt gates the build at zero issues. Ktlint runs advisory.

## Prerequisites

- JDK 17 on your path. Module toolchains target 17. The Gradle daemon runs on a JDK 21 toolchain, and the build provisions it through the foojay resolver if you lack one.
- Android SDK with the `android-37.0` platform. It publishes through the preview channel, so install it with channel 3: `sdkmanager --channel=3 "platforms;android-37.0"`.
- NDK 28.2.13676358. The whisper-android native build pins this version: `sdkmanager "ndk;28.2.13676358"`.
- An arm64 Android device or an arm64 emulator image. The app builds for arm64-v8a only and runs on Android 14 (API 34) and up. Compile SDK is 37, target SDK is 36.

## Cold-clone setup

```bash
git clone --recurse-submodules <this repo's URL>
cd bloviate
```

Two levels of submodules matter here. Bloviate includes whisper-android, and whisper-android includes whisper.cpp. A plain clone followed by `git submodule update --init --recursive` gets the same tree.

One snag to know about before you start: `.gitmodules` still points whisper-android at its old GitLab address. That GitLab project is private, so the submodule step fails for anyone outside the old group. Until the URL is updated in this repo, clone `github.com/shippopotamuslabs/whisper-android` into `third_party/whisper-android` by hand and run the same recursive submodule step inside it for whisper.cpp.

The included whisper-android build resolves the SDK location per build. Give it a `local.properties` of its own:

```bash
cp local.properties third_party/whisper-android/local.properties
```

Your root `local.properties` holds the usual `sdk.dir=...` line. Both files are gitignored in both repos.

`app/google-services.json` is committed in this repo. It names the production Firebase project, `bloviate-app`. A build from a fresh clone uses it as it sits: sign-in, crash reports, and function calls go to that project. To run your own backend, register the package name `com.shippopotamus.bloviate` in your own Firebase project, download its `google-services.json`, and replace the file. The cloud functions deploy from `functions/` with the Firebase CLI against whichever project you target.

Release signing reads `keystore.properties` in the repo root. The file is gitignored; `keystore.properties.example` shows the shape. Debug builds ignore all of this. A release build made with no `keystore.properties` signs with the debug key, which is fine for trying the app and wrong for shipping it.

## Build and install

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install it on a connected device with:

```bash
./gradlew :app:installDebug
```

A signed release build, given a filled-in `keystore.properties`:

```bash
./gradlew :app:assembleRelease
```

First run on the device walks through the setup the app needs: turn on the accessibility service so the bubble can spot text fields, grant the overlay permission so the bubble can draw, allow the microphone, exempt the app from battery optimization so the service stays resident, and download a dictation model. Parakeet comes down by default at about 640 MiB. Cloud styles ask for Google sign-in and the subscription at the point you pick one.

## Tests

Android unit tests run on the JVM. The full suite:

```bash
./gradlew testDebugUnitTest
```

Narrower runs work per module, for example `./gradlew :core:domain:testDebugUnitTest`. The suite holds 167 test files across the four modules: 56 in `:app`, 53 in `:core:domain`, 36 in `:core:data`, 22 in `:core:application`. Robolectric covers the framework-bound logic like accessibility nodes and text insertion. The testing contract prefers hand-written fakes over mocks and JUnit 5 over JUnit 4; `contracts/UNIT_TESTING.md` has the rules. This repo has no instrumented test suite and no screenshot test suite at present.

Static analysis gates merges:

```bash
./gradlew detekt
```

Detekt runs at `maxIssues: 0` and the `assemble` tasks depend on it. `./gradlew ktlintCheck` runs advisory with failures ignored.

Function tests compile first, then run on the Node test runner:

```bash
cd functions
npm install
npm run build
npm test
```

Eight test files cover the style chain, the prompt builder, the output guard, entitlement, and custom styles.

## CI and distribution

A GitHub Actions port of the pipeline is in progress. The GitLab pipeline it replaces ran detekt and lint as one static analysis job, the unit test suite, debug and release assemblies, and a distribution job on every main merge.

Distribution today is Firebase App Distribution. Every merge to main ships the release-signed APK to the Internal tester group. The Play Store listing is in preparation. Sideloading the debug or release APK works for anyone who builds it.

## Status

Version 1.0, versionCode 1. The app is in daily use by its author and a small internal group. The code is public so the work can be read, built, and forked. The production Firebase project, the signing key, and the model provider accounts stay private, and the subscription is a live product: forks get the full client and function source and bring their own backend.
