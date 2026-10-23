# CODE_STYLE_RULES.md
**Bloviate – Code Style Contract v1.0**
(Adapted from Enclave `CODE_STYLE_RULES.md` v2.4.)

Kotlin and Compose idioms, naming, formatting, and stability rules.

---

## Strict Boundaries

| Always | Never |
| :--- | :--- |
| Use `val` over `var` and immutable collections by default. | Use `!!` (non-null assert) without `requireNotNull()`. |
| Accept `modifier: Modifier = Modifier` as the **last** parameter. | Apply the passed `modifier` to an inner element instead of the outermost layout. |
| Use `kotlinx.collections.immutable` in UI state classes. | Scatter `@Stable` or `@Immutable` preemptively without profiling. |
| Prefer method references (`::onModeChange`) for callbacks. | Create new lambda instances inside `LazyColumn` or `LazyRow` item blocks. |
| Use `stateIn` with `WhileSubscribed(5_000)` for ViewModel state. | Manually `.collect` and `.update` state inside `viewModelScope.launch`. |
| Use `collectAsStateWithLifecycle()` in Compose. | Use `collectAsState()`, `GlobalScope`, or `runBlocking`. |
| Use `derivedStateOf` for state that depends on other state. | Perform heavy calculations or filtering directly in the Composable body. |
| Prefer Slot APIs (`content: @Composable () -> Unit`) for layout flexibility. | Pass 10+ specific data parameters into a single "God Composable." |
| Hoist business state to the ViewModel. | Hoist ephemeral UI state (focus, animation) out of Compose unnecessarily. |
| Use `CompositionLocal` only for truly cross-cutting themes/configs. | Use `CompositionLocal` to pass data that should be explicitly passed via parameters. |
| Comment the "why" when logic is non-obvious. | Comment the "what" or leave `// TODO` comments in merged code. |
| Use `state.update { }` to update `MutableStateFlow` | Use `state.value =` |

## 1. Kotlin Conventions

- Kotlin 2.x with K2 compiler.
- Explicit return types on all public functions and properties.
- `val` over `var`. Immutable collections by default.
- `when` over `if-else` chains for 3+ branches.
- No `!!` (non-null assert). Use `requireNotNull()` with a message, or handle nulls explicitly.
- Extension functions for repeated utility logic; keep them in focused files, not scattered.
- Named arguments on any call with 3+ parameters or where meaning is ambiguous.

## 2. Naming

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
| Domain Ports | Port suffix on the interface | `TranscriptionPort`, `ModelStorePort` |

## 3. File Organization

- One public top-level composable per file.
- Helper composables and preview composables go below the main public composable as `private`.

## 4. Compose-Specific Rules

### 4.1 Stability
- UI state classes: `data class` (stable by default when all fields are stable types).
- If a class contains `List`, `Map`, or other mutable collection types, either:
  - Use `kotlinx.collections.immutable` (`ImmutableList`, `PersistentList`), OR
  - Annotate with `@Immutable` / `@Stable` if you guarantee no mutation.
- **Do not scatter `@Stable` annotations preemptively.** Only add when profiling confirms unnecessary recomposition from that specific type.

### 4.2 Modifier Conventions
- Every composable that emits layout accepts `modifier: Modifier = Modifier` as its **last** parameter.
- Apply `modifier` to the outermost layout element.
- Modifier order matters: `padding` before `background` clips differently than the reverse. Follow the logical layering.

### 4.3 State Hoisting
- Composables that manage text input: accept `value: String` + `onValueChange: (String) -> Unit`.
- Internal ephemeral state (`remember { mutableStateOf(...) }`) is fine for dropdown expanded, focus, animation state.
- Business state always hoisted to ViewModel.

### 4.4 Lambdas
- Prefer method references (`viewModel::onModeChange`) over inline lambdas when passing to child composables.
- Avoid creating new lambda instances in Lazy item blocks (causes recomposition). Extract to `remember` or use stable references.

### 4.5 Rigor Protocols
- **Performance Deferral:** High-frequency state (live RMS volume, waveform samples, download progress percentages) must be passed to leaf composables as a lambda `() -> Float`. This ensures the root screen/bubble does not recompose on every audio frame or byte update.
- **Visual Purity (Canvas):** If a progress indicator artifact appears, use explicit overrides like `drawStopIndicator = {}` and `gapSize = 0.dp`. Use `StrokeCap.Butt` with a parent `Modifier.clip` to ensure seamless rounding.

### 4.6 Plausible Composition
- All `LazyColumn` and `LazyRow` components must use stable keys.
- Ensure state reads are deferred using lambda-based modifiers (e.g., `Modifier.offset { ... }` or `drawBehind`) to minimize recomposition.

## 5. Coroutines & Flow

- `viewModelScope.launch` for ViewModel work; an injected, scope-owned `CoroutineScope` for service-layer work (never a scope leaked from a composable).
- `collectAsStateWithLifecycle()` (not `collectAsState()`) for StateFlow in composables.
- `LaunchedEffect(Unit)` for one-shot event collection from `Channel`/`Flow`.
- Never use `GlobalScope`.
- Never use `runBlocking` in production code.
- Native/transcription calls are serialized (see `TRANSCRIPTION_RULES.md`); never call the engine concurrently.

## 6. KDoc & Comments

- KDoc on public classes and non-obvious public functions only.
- Inline comments only where the "why" is non-obvious. Never comment the "what" when the code is self-explanatory.
- No `TODO` comments in merged code — use the issue tracker.
- File-level comments only for contracts or architectural rationale.

## 7. Formatting

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
