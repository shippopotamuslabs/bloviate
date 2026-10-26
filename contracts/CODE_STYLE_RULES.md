# CODE_STYLE_RULES.md
**Bloviate – Code Style Contract v1.0**
(Adapted from Enclave `CODE_STYLE_RULES.md` v2.4.)

Kotlin and Compose idioms, naming, formatting, and stability rules.

> **GLO-13 (2026-10-25):** the shared code style rules now live in the
> gospels submodule: `contracts/gospel/CODE-STYLE-GOSPEL.md`. This
> contract keeps Bloviate's example tables, local rule variants, and
> enforcement lines; the gospel carries the shared rule text.

---

## Strict Boundaries

The strict-boundaries table (val-over-var, modifier-last, immutable UI
state, method references over new lambdas, `stateIn` with
`WhileSubscribed(5_000)`, `collectAsStateWithLifecycle()`,
`derivedStateOf`, slot APIs, business state in the ViewModel,
`CompositionLocal` discipline, comment-the-why, `state.update { }`) lives
in the Code Style Gospel: `contracts/gospel/CODE-STYLE-GOSPEL.md`
("Strict Boundaries").

## 1. Kotlin Conventions

The Kotlin conventions (Kotlin 2.x / K2, explicit return types on public
API, `val` over `var`, immutable collections, `when` over long if-else
chains, no `!!`, extension functions in focused files, named arguments)
live in the Code Style Gospel: `contracts/gospel/CODE-STYLE-GOSPEL.md` §1.

## 2. Naming

The naming conventions live in the Code Style Gospel:
`contracts/gospel/CODE-STYLE-GOSPEL.md` §2. Bloviate's examples:

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase dot-separated | `com.shippopotamus.bloviate.presentation` |
| Class / Object | PascalCase | `SettingsViewModel`, `Routes` |
| Function | camelCase, verb-first | `onStartListening()`, `calculateBubbleOffset()` |
| Composable | PascalCase (noun) | `BubbleContent()`, `ModelPicker()` |
| Property | camelCase | `isListening`, `selectedModel` |
| Constant | UPPER_SNAKE_CASE | `MAX_DICTATION_SECONDS` |
| Private backing flow | `_uiState` pattern | `private val _uiState = MutableStateFlow(...)` |
| Resource IDs | snake_case | `R.drawable.ic_mic`, `R.string.model_base_en` |
| Application Ports | Port suffix on the interface | `TranscriptionPort`, `ModelStorePort` |

## 3. File Organization

The file organization rules (one public top-level composable per file;
helper and preview composables below it as `private`) live in the Code
Style Gospel: `contracts/gospel/CODE-STYLE-GOSPEL.md` §3.

## 4. Compose-Specific Rules

Shared stability, modifier, state-hoisting, lambda, rigor, and
composition rules: `contracts/gospel/CODE-STYLE-GOSPEL.md` §4 (§§4.1–4.4
and §4.6 live there only). Bloviate's §4.5 below is kept in full — its
Performance Deferral rule is written for Bloviate's audio-frame and
download state and outranks the gospel's generic wording where they
differ.

### 4.5 Rigor Protocols
- **Performance Deferral:** High-frequency state (live RMS volume, waveform samples, download progress percentages) must be passed to leaf composables as a lambda `() -> Float`. This ensures the root screen/bubble does not recompose on every audio frame or byte update.
- **Visual Purity (Canvas):** If a progress indicator artifact appears, use explicit overrides like `drawStopIndicator = {}` and `gapSize = 0.dp`. Use `StrokeCap.Butt` with a parent `Modifier.clip` to ensure seamless rounding.

## 5. Coroutines & Flow

The coroutines and Flow rules (`viewModelScope`, injected scope-owned
scopes for services, `collectAsStateWithLifecycle()`, `LaunchedEffect`
one-shot collection, no `GlobalScope`, no `runBlocking` in production)
live in the Code Style Gospel: `contracts/gospel/CODE-STYLE-GOSPEL.md` §5.

**Bloviate local rule:** native/transcription calls are serialized (see
`TRANSCRIPTION_RULES.md`); never call the engine concurrently.

## 6. KDoc & Comments

The KDoc and comment rules (KDoc on public classes and non-obvious public
functions only; inline comments for the "why"; no `TODO` comments in
merged code — use the issue tracker; file-level comments only for
contracts or architectural rationale) live in the Code Style Gospel:
`contracts/gospel/CODE-STYLE-GOSPEL.md` §6.

## 7. Formatting

Shared formatting rules: `contracts/gospel/CODE-STYLE-GOSPEL.md` §7.
Bloviate's version below adds the local Detekt line-length allowance.

- 4-space indent (Kotlin standard).
- Max line length: 120 characters (Detekt allows 140; keep to 120 unless a construct genuinely needs more).
- Trailing commas on multi-line parameter lists and `when` branches.
- Blank line between functions. No multiple consecutive blank lines.
- Import ordering: Kotlin stdlib → Android/Compose → Third-party → Project. Let the IDE sort.
- Enforced by the ktlint Gradle plugin (advisory, `ignoreFailures` on — same as Enclave) and Detekt (`config/detekt/detekt.yml`, hard gate at `maxIssues: 0`).

## 8. Testing Conventions

- Unit tests: JUnit 5 + Turbine for Flow testing + **hand-written fakes** for Bloviate-owned ports (see `UNIT_TESTING.md`). Fakes live in the module's `src/test` source set until a shared testing module exists. MockK only for third-party SDK boundaries with `@Suppress` + justification; no `verify` in ViewModel tests.
- Compose UI tests: `createComposeRule()` with semantic matchers.
- Test names: `fun methodName_condition_expectedResult()` or backtick descriptive names.
- Fakes for previews live alongside models. Fakes for tests live in the test source set (shared fakes move to a shared testing module in the same MR that creates it).
- Architecture/style beyond Detekt's built-in rules is agent-enforced (see `ARCHITECTURE_RULES.md` §3.4): ViewModels use-case-only, no domain types in composables/UiState. `./gradlew detekt` must pass at `maxIssues: 0`.
- All work is ticketed in Linear (team `BLO`, project Bloviate) per `AGENTS.md` §3.7 — one MR per sub-issue, `Fixes BLO-xx` in the MR description.

### 8.1 Test Coverage Discipline (Agent Mandate)

- Whenever the task **adds** new production code (new classes, public functions, significant logic in `domain/`, `data/`, `service/`, ViewModels, repositories, mappers, use cases, etc.) **or modifies** existing code, the agent **MUST**:
  - Write new unit tests (or extend existing ones) in the matching `src/test/kotlin` source set that cover the new/modified behavior, main paths, edge cases, and error conditions.
  - Use JUnit 5 + Turbine (for `StateFlow`/`Flow`) + fakes as defined above and in `UNIT_TESTING.md`.
- For pure UI/Compose changes that affect behavior or interactions: also add or update Compose UI tests using `createComposeRule()` and semantic matchers.
- Trivial styling-only or preview-only changes are exempt from new tests, but the full test suite must still pass.
- Before declaring `TASK_STATUS: COMPLETE`, the agent must have written/updated the tests **and** executed them successfully.

---

## 9. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Check Formatting:** Run `./gradlew ktlintCheck` or a module-scoped ktlint task when iterating. Fix any reported styling errors.
2. **Run Tests:** Run the narrowest affected test task, e.g. `./gradlew :app:testDebugUnitTest --tests "*ChangedClass*"`, then broaden to `./gradlew testDebugUnitTest` before merge-sized changes.
3. **Run Build:** Run `./gradlew :app:assembleDebug`.

## 10. 2026 Exception & Notification Discipline

### 10.1 Worker Exception Handling
Canon: `contracts/gospel/CODE-STYLE-GOSPEL.md` §9. Bloviate's table:

| Always | Never |
|--------|-------|
| Explicitly catch and rethrow `CancellationException`. | Catch `Exception` generically without re-throwing cancellation. |
| Return `Result.retry()` on transient network/IO failures. | Return `Result.failure()` for recoverable quota timeouts. |

### 10.2 Notification Few-Shot (API 36 Compliance)
**Correct Implementation:**
```kotlin
val builder = NotificationCompat.Builder(context, CHANNEL_ID)
if (Build.VERSION.SDK_INT >= 36) {
    builder.setStyle(Notification.ProgressStyle().setProgress(progress).setStyledByProgress(true))
} else {
    builder.setProgress(100, progress, false)
}
```

*Incorrect Implementation*
```kotlin
// Legacy: Will be deprioritized in the 2026 System UI
builder.setProgress(100, progress, false)
builder.setContentText("$progress% complete")
```
