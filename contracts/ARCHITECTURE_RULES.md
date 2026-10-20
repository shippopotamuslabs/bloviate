# ARCHITECTURE_RULES.md
**Bloviate – Structural Architecture Contract v1.0**
(Adapted from Enclave `ARCHITECTURE_RULES.md` v2.4 for a single-module app.)

Non-negotiable architectural rules. Always enforce together with relevant companion contracts.

---

## 0. Platform Compliance Anchors

- **Foreground service rules (Android 14+):** the bubble service runs as a foreground service of type `microphone`. It must be started from a state the platform allows; the accessibility-triggered start path is a known platform risk and any change to it needs on-device verification (Pixel + Motorola at minimum).
- **Background work:** model downloads run through `WorkManager`. `dataSync` foreground services have a 6-hour cumulative timeout — downloads must be resumable from byte offsets and must survive process death.
- **State integrity:** `WorkManager` progress/state is the sole bridge for background-to-UI state. No singleton callbacks or static listeners for background progress.
- **API 36 UX:** `ProgressStyle` notifications are the primary interface for active journeys (model downloads); see `CODE_STYLE_RULES.md` §10.2.

**Clean Architecture Supremacy Rule (ZERO TOLERANCE – HIGHEST PRIORITY)**

This project follows Clean Architecture / ports-and-adapters. Dependencies point
inward toward the domain layer; data, service, and presentation code adapts
framework, storage, platform, and UI concerns around domain ports.

**The agent MUST NEVER:**
- Write, propose, or allow any code that violates the dependency rule.
- Leak Android, Compose, Hilt, accessibility-framework, or `whisper-android` implementation types through `domain/` public APIs.
- Bypass layer boundaries, forbidden imports, or layer responsibilities.
- Use any other architectural pattern (MVVM-only, monolithic, etc.).

**Violation = immediate contract breach.**
Reply with exactly: "Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."
Then stop. Do not generate code until waived.

All rules below are non-negotiable implementations of Clean Architecture.

---

## Strict Boundaries Expansion

| Always | Never |
|--------|-------|
| Route background progress via `WorkManager.setProgress`. | Use singleton callbacks or static listeners for background-to-UI updates. |
| Use `ExistingWorkPolicy.KEEP` or `REPLACE` for unique work. | Scatter multiple unmanaged `OneTimeWorkRequests` for the same task. |
| Implement byte-offset resumption for IO tasks (model downloads). | Assume a background task will run to completion in a single session. |
| Route navigation via pure callbacks hoisted to the route level. | Pass `NavController` into Composables. |
| Expose a single `StateFlow<UiState>` from ViewModels. | Expose mutable flows or Android framework types from ViewModels. |
| Ensure each screen owns its own `Scaffold` and `TopAppBar`. | Thread `paddingValues` down the UI tree from a root-level Scaffold. |
| Consume one-shot events via `Channel<Event>` and `LaunchedEffect`. | Use `SharedFlow` for one-shot UI events (can cause dropped events). |
| Use stable keys on all `LazyColumn` and `LazyRow` items. | Import `data/`, `service/`, `presentation/`, or library-implementation code into `domain/`. |
| Recycle every `AccessibilityNodeInfo` obtained from the accessibility framework. | Hold node references across events. |
| Keep the overlay window `FLAG_NOT_FOCUSABLE` (see §5.2). | Let the bubble window take focus — it steals the host field's caret and the bubble hides itself. |

## 1. Layer Boundaries (single-module form)

Bloviate is one `:app` module today; layers are **packages** under `com.shippopotamus.bloviate`:

```text
:app
 ├─ domain/        models, ports (*Port), use cases — framework-free
 ├─ data/          port implementations: persistence, downloads, platform adapters
 ├─ service/       FieldWatcherService, BubbleOverlayService, insertion
 ├─ presentation/  Compose UI, ViewModels, presentation models
 └─ di/            Hilt wiring (the only place ports meet implementations)
```

### 1.1 Dependency Direction (Strict)

```text
presentation/  → domain/            (ViewModels call use cases; UI renders UiState)
service/       → domain/            (services drive use cases / ports)
data/          → domain/            (implements ports)
di/            → domain/, data/, service/   (composition root)
domain/        → nothing else in the app; no Android framework types in public APIs
whisper-android (library) → consumed behind a domain port; its types do not leak
                            into domain/ or presentation/ APIs
```

**Growth path:** if a layer outgrows the app module, split it into Gradle modules
with exactly Enclave's graph — `:core:domain` (no dependencies), `:core:data` →
`:core:domain`, `:core:ui` → `:core:domain`, `:core:testing` → `:core:domain`,
`:app` → all. The package rules in this contract are written to survive that split
unchanged; package names map to modules one-to-one.

No reverse dependencies into `domain/` are allowed.

### 1.2 Forbidden Imports

| Package | Must NOT import |
|--------|-----------------|
| `domain/` | `data/`, `service/`, `presentation/`, Compose, Hilt annotations on domain types, Android framework types in public APIs, `com.shippopotamus.whisper` implementation classes |
| `data/` | Compose UI |
| `presentation/` | `data/` classes, `com.shippopotamus.whisper` types (go through domain ports/use cases) |

Reusable UI code lives with the presentation layer; screens are wired by the app shell.

---

## 2. Layer Responsibilities

- **`domain/`** — Domain models, ports, use cases. Keep domain APIs free of Android/UI/storage/native implementation types. Owns the pipeline seams: `TranscriptionEngine`, `TextPostProcessor`, `ProcessingMode` (see `TRANSCRIPTION_RULES.md`).
- **`data/`** — Implements persistence (DataStore, files), model downloads (WorkManager), and platform adapters behind domain ports. No Compose UI.
- **`service/`** — The two long-lived platform services (accessibility watcher, bubble overlay) and text insertion. Services are thin: they translate platform events into use-case calls and render bubble state.
- **`presentation/`** — Compose UI and presentation logic (settings, onboarding, model manager). ViewModels map domain → presentation models.
- **`:app` shell / `di/`** — Application class, navigation wiring, DI composition, app resources, platform entrypoints.

---

## 3. Presentation State Contract

### 3.1 UI State
- Immutable `data class`. All updates via `copy()`.
- No mutable fields. No Android types inside UI state.
- One `StateFlow<UiState>` per ViewModel.

### 3.2 One-Shot Events
- Use `Channel<Event>(Channel.BUFFERED)` exposed as `Flow<Event>` via `receiveAsFlow()`.
- Consumed in `LaunchedEffect(Unit)` at the screen level.

### 3.3 Model Mapping
- Domain models MUST NOT be passed directly to composables. ViewModel maps domain → presentation.
- Presentation models (`*UiState`, `*UiEvent`, drafts) MUST NOT embed domain types; map at the ViewModel/mapper boundary.
- No repository calls inside composables.

### 3.4 ViewModel Dependencies & Flow Composition
- **ViewModels depend on use cases, not repository or port interfaces.** A ViewModel constructor injects `*UseCase` classes from `domain/`; it must not inject a `*Repository`/`*Port` interface or any `data/` type directly. Ports are consumed by use cases.
- **Mappers must not accept use-case parameters.** Mappers are pure domain → presentation transforms; ViewModels invoke use cases and pass derived values into mappers.
- **Use cases own multi-source `Flow` composition.** When a screen needs more than one source, add a domain use case that combines the streams into one domain read model; the ViewModel observes that single use case.
- **Only transient/presentation state lives in the ViewModel** (unsaved drafts, reveal-errors flags, one-shot events). Durable facts are read through use cases.
- **Write paths go through use cases too** — never `repository.save(...)` from a ViewModel.
- These rules are agent-enforced today. Enclave enforces the twin rules with a custom Detekt rule set; if Bloviate grows one, this section is its spec. Do not add suppressions to hide layering debt — file an ENC sub-issue instead.

### 3.5 Linear Ticketing
All implementation work follows `AGENTS.md` §3.7 (Linear team `ENC`, project Bloviate, chunk-based trunk workflow, one MR per sub-issue, `Fixes ENC-xx` in the MR description). Architecture changes without a ticket are out of contract.

---

## 4. Navigation & Scaffold Architecture

Each screen owns its own `Scaffold`, `TopAppBar`, and `ViewModel`. There is no global/root `Scaffold`.

```kotlin
// SettingsRoute.kt — wires ViewModel to Screen
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(uiState = uiState, onBack = onBack)
}
```
- **No `DynamicTopBar`.** Each screen composes its own TopAppBar.
- **No `paddingValues` threading.** Each Scaffold consumes its own inner padding locally.

---

## 5. Platform Services Architecture (Bloviate-specific)

### 5.1 FieldWatcherService (AccessibilityService)

Android offers exactly one API that can tell whether an editable field in *another* app currently holds the caret: an AccessibilityService.

- Subscribes to view-focused, window-state, window-content, and text-changed events with `notificationTimeout ≈ 80 ms`.
- On each event: `findFocus(FOCUS_INPUT)`; treat the field as editable if `isEditable` **or** the class name contains `EditText` (WebView/legacy inputs lie about `isEditable`).
- Show the bubble when the keyboard is visible **and** an editable, **non-password** field is focused (ENC-125); dismissing the keyboard hides the bubble even if the field keeps focus. Password/secure fields: **never** show, never inspect content. An in-progress recording/processing bar is exempt from keyboard-driven hide — a keyboard dismissal while the bar is up defers and lands when the bar settles to Idle; a password-field hide is never deferred and cancels the recording immediately. Keyboard visibility is derived from the input-method window's presence in the same accessibility window stream (window type only — no content is ever read, and no new permissions are involved).
- Recycle every `AccessibilityNodeInfo`. No node state survives an event.
- The service reports field focus through a domain port; it contains no transcription or insertion logic of its own.

### 5.2 BubbleOverlayService (Foreground Service + WindowManager)

- `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS`. **`FLAG_NOT_FOCUSABLE` is non-negotiable:** if the overlay takes focus, the text field loses its caret, `findFocus(FOCUS_INPUT)` returns null, and the bubble hides itself.
- Foreground service type `microphone` (Android 14+ requirement for mic capture from a service). Until `RECORD_AUDIO` is granted the service starts as `specialUse`; it (re)starts as `microphone` once the permission is granted — Android 14+ refuses a microphone-type start without the grant.
- Keeps the transcription engine loaded/warm while enabled (see `TRANSCRIPTION_RULES.md`).
- Bubble states: **Idle** (mic bubble), **Listening** (the recording bar: [X] [live waveform] [✓] + elapsed — no pulsing ring; the waveform is the amplitude affordance), **Transcribing** (shimmering status text in the waveform slot — "Transcribing" while ASR runs, "Polishing" while the AI style pass runs; local-only sends skip Polishing), **Done** (brief check), **Error** (red flash).
- Gestures (**tap-only — there is deliberately NO hold-to-talk / push-to-talk**): tap bubble = start recording and morph into the recording bar; ✓ on the bar = stop + transcribe + insert; X on the bar = cancel (discard, insert nothing); drag = reposition (position persisted); drag to bottom = dismiss for this field session (during recording: cancel + dismiss).
- Default anchor: just above the keyboard, screen-bottom offset; user-draggable (keyboard heights vary too much to compute reliably). Must survive rotation and fold/unfold without leaking windows or landing off-screen.

### 5.3 Text Insertion — Paste First

Via the accessibility service on the focused node:
1. Set clipboard to the processed text, `performAction(ACTION_PASTE)` — respects the caret/selection and the host app's own input handling.
2. Fallback only: splice at `textSelectionStart/End` with `ACTION_SET_TEXT`. This replaces the whole field, so it is **never** the default.
- A failed transcription/insertion leaves the user's existing text untouched and keeps the audio buffer long enough for one retry tap.

### 5.4 Permissions

Onboarding gates, each with a deep link to the system setting: display over other apps (`SYSTEM_ALERT_WINDOW`), accessibility service enabled, microphone (`RECORD_AUDIO`). No account, no analytics.

---

## 6. IME / Keyboard Handling

Bloviate never replaces the keyboard and never hosts an IME. For its own screens (settings, onboarding test field):

```kotlin
@Composable
fun SettingsScreen(uiState: SettingsUiState, onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0) // prevent Scaffold from consuming IME
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // screen content
        }
    }
}
```
- Manifest: `android:windowSoftInputMode="adjustResize"` on `MainActivity`.
- Setup: `enableEdgeToEdge()` must be called in `MainActivity.onCreate()`.

---

## 7. Accessibility

- All interactive elements must have `contentDescription` + appropriate semantic `role`.
- Use `sp` units for text. System font scaling must be respected.
- TalkBack must work across all custom components — including a usable non-bubble path through every Bloviate screen.

---

## 8. Preview Requirements

Every composable file MUST include previews for:
- Light + Dark themes
- Empty state (where applicable)
- Populated / long content
- Loading state (where applicable)

*Constraint:* Preview composables must NOT reference Hilt or real ViewModels. Use fake presentation models.

---

## 9. Performance & Targets

- **Target:** Android 14+ (minSdk 34, targetSdk 36). Reference devices: Pixel 8 Pro, Motorola Razr Fold.
- **Stack:** Jetpack Compose (current stable per the version catalog), Material 3 only, Hilt for DI, Kotlin Coroutines + Flow for async.
- No blocking work in composables. No audio or transcription work on the main thread.
- The stop→insert path is the product's latency budget: nothing may be added to it without measuring on device (see `TRANSCRIPTION_RULES.md`).

## 10. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check App Compilation:** Run `./gradlew :app:assembleDebug`.
2. **Verify Imports/Style:** Run `./gradlew ktlintCheck` and `./gradlew detekt` (required, `maxIssues: 0`).

## 11. Sequential Reasoning: Background-Work Audit
Before implementing any background or state-heavy task, the agent must perform the following internal audit:

1. **Quota Check:** Is this task likely to exceed the 6-hour `dataSync` limit? If yes, implement `Result.retry()` and byte-range resumption.
2. **Exemption Check:** Is the worker triggered by a user-initiated action to ensure foreground service eligibility?
3. **UI Bridge Check:** Am I using `WorkManager` state/progress to pipe progress? (Verify no custom callbacks are used.)
4. **API Style Check:** Does the notification utilize `ProgressStyle` for API 36+?

**Mandate:** If any of these checks fail, the agent must correct the plan before writing the first line of Kotlin code.
