# Unit Testing Contract — Bloviate

This contract defines the mandatory standards for unit testing in the Bloviate project. It prioritizes **Fakes over Mocks**, **JUnit 5**, and **Robolectric** for high-fidelity JVM testing. (Adapted from Enclave's contract of the same name.)

---

## 1. Core Principles (ALWAYS / NEVER)

| Rule | Description |
| :--- | :--- |
| **ALWAYS** Prefer Fakes | Use real, lightweight implementations of ports (e.g., `FakeTranscriptionEngine`, `FakeModelStore`) instead of programming mock expectations. |
| **ALWAYS** JUnit 5 | Use `org.junit.jupiter.api` annotations (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`). |
| **ALWAYS** State-Based Assertions | Assert on the *result* of an operation (e.g., the value in a StateFlow) rather than the *process* (e.g., "was this method called"). |
| **ALWAYS** Use Turbine | Use the Turbine library for testing Kotlin Flows and StateFlows to ensure event-by-event verification. |
| **ALWAYS** MainDispatcherExtension | In JUnit 5, use a custom `MainDispatcherExtension` to swap `Dispatchers.Main` for a `TestDispatcher`. |
| **NEVER** Mock Android Classes | Do not mock `Context`, `SharedPreferences`, or `Bundle`. Use Robolectric's real implementations or appropriate fakes. |
| **NEVER** Mock Owned Ports | Do not `mockk` Bloviate ports defined in `domain/`. Use hand-written fakes. |
| **NEVER** `verify()` ViewModels | Do not use MockK `verify` / `coVerify` in `*ViewModelTest.kt` — assert UI state instead. |
| **NEVER** Use JUnit 4 in New Tests | Avoid `@Rule`, `@Test(expected=...)`, and `@Before`. Migrated tests must be JUnit 5 compliant. |
| **NEVER** Mock Implementation Details | Avoid mocking private methods or internal helper classes. Test the public API of the unit. |
| **NEVER** Await Fire-and-Forget IO with `delay()` | Inside `runTest`, `delay(N)` advances *virtual* time (~0 wall-clock) and does **not** wait for `scope.launch(Dispatchers.IO) { … }`. Inject a `TestDispatcher` for the IO path and `advanceUntilIdle()`, or `join()` the background Jobs. See §3.3. |

---

## 2. Technical Stack

- **JUnit 5:** The engine for all local unit tests.
- **Fakes:** Shared hand-written fakes (`Fake<Port>` per domain port, with `seed`/`setShouldFail`/`clear` toggles) live in the app module's `src/test` source set today. Bloviate has **no `:core:testing` module yet** — when one is created, a new domain port must ship its fake there in the same MR. Feature-local fakes only when single-use.
- **MockK:** Use only when a Fake is impractical (third-party SDK boundaries, static Android APIs). Requires `@file:Suppress(...)` with a one-line justification comment. Never for Bloviate-owned ports, never `verify` in ViewModel tests.
- **Turbine:** For all Flow/StateFlow assertions.
- **Robolectric:** For tests requiring Android framework APIs on the JVM (e.g., accessibility node handling, clipboard/insertion logic).
- **StandardTestDispatcher:** Always use `StandardTestDispatcher` with `runTest` for coroutine testing.

---

## 3. Pattern Examples

### 3.1 ViewModel Testing (JUnit 5 + Turbine + Fakes)

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class DictationViewModelTest {

    private val fakeEngine = FakeTranscriptionEngine()
    private lateinit var viewModel: DictationViewModel

    @BeforeEach
    fun setup() {
        viewModel = DictationViewModel(TranscribeUseCase(fakeEngine))
    }

    @Test
    @DisplayName("Given a recorded utterance, when stop is tapped, then cleaned text is emitted")
    fun testTranscribe() = runTest {
        viewModel.uiState.test {
            assertEquals(DictationUiState.Idle, awaitItem())

            viewModel.onStop()

            assertTrue(awaitItem() is DictationUiState.Transcribing)

            val done = awaitItem() as DictationUiState.Done
            assertEquals("Hello world.", done.text)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### 3.2 Fakes over Mocks

**Wrong (Brittle Mock):**
```kotlin
val mockEngine = mockk<TranscriptionEngine>()
coEvery { mockEngine.transcribe(any()) } returns "hello"
// This fails if the internal implementation changes how transcribe is called.
```

**Right (Robust Fake):**
```kotlin
class FakeTranscriptionEngine : TranscriptionEngine {
    var nextTranscript: String = ""
    var shouldFail: Boolean = false
    override suspend fun transcribe(pcm: FloatArray): String {
        if (shouldFail) throw IllegalStateException("fake failure")
        return nextTranscript
    }
}
```

### 3.3 Awaiting Fire-and-Forget Persistence (`runTest` + `Dispatchers.IO`)

A store that updates an in-memory cache synchronously but persists asynchronously — `scope.launch(Dispatchers.IO) { file.write(...) }` — **cannot** be awaited with a virtual-time poll inside `runTest`. This is exactly the shape of Bloviate's model-file finalize and history persistence paths.

**Why it flakes:** `delay(N)` inside `runTest` advances *virtual* time, not wall-clock time, so it completes in ~0 ms regardless of `N`. The real `Dispatchers.IO` coroutine racing it may not have run yet, so the assertion fires against stale state. Increasing `N` does not help.

**Wrong (flaky poll):**

```kotlin
store.commit(id, content)
repeat(20) { delay(50) }            // virtual time only; races Dispatchers.IO
store.clear(id)
repeat(20) { delay(50) }
assertNull(store.get(id))           // intermittent failure
```

**Right — option A (preferred): inject a `TestDispatcher` for the IO path.**
Make the persistence dispatcher a constructor parameter and pass a `TestDispatcher` in tests, then advance virtual time deterministically:

```kotlin
val store = MyStore(files, scope, testDispatcher)
store.commit(id, content)
advanceUntilIdle()                  // deterministic — no real threads
```

**Right — option B: `join()` the background Jobs.** When the class owns a real `CoroutineScope`, join its child coroutines before asserting:

```kotlin
store.commit(id, content)
testScope.awaitBackgroundWrites()   // joins child Jobs of the injected scope
store.clear(id)
testScope.awaitBackgroundWrites()
assertNull(store.get(id))
assertNull(files.read(id))          // assert both cache AND disk
```

Enclave's precedent for option A: `DownloadWorkRepository` injects its `ioDispatcher` and its chain test passes `UnconfinedTestDispatcher()`. Bloviate's download stack is a port of Enclave's — follow whichever option matches the class's construction; never bridge real-dispatcher work with a `delay()` poll.

---

## 4. Golden Test References

Pattern references (from the Now in Android project, as used by Enclave and Stratum):

- **Repository Fakes/Tests:** `OfflineFirstUserDataRepositoryTest.kt`
- **ViewModel JUnit 5 Patterns:** `SettingsViewModelTest.kt`
- **Robolectric / Android Logic:** `ForYouViewModelTest.kt`
- **Coroutines Utility:** `MainDispatcherRule.kt` (Note: Adapt to `MainDispatcherExtension` for JUnit 5).

When this repo vendors local copies under `golden-test-examples/`, prefer the local copies.

---

## 5. Implementation Mandate

Any agent creating new features **MUST** include a matching unit test file in the `src/test` directory of the modified module, following these standards. Run the narrowest affected test task during iteration (for example, `./gradlew :app:testDebugUnitTest --tests "*DictationViewModelTest*"`), then broaden to `./gradlew testDebugUnitTest` for merge-sized or cross-module changes.

### 5.1 Self-check (anti reward-hacking)
After writing a test, break the production code under test (wrong return value, skipped persistence). The test **must fail**. If it still passes, the test is invalid — rewrite it before marking work complete.

### 5.2 Detekt & Linear gates
`./gradlew detekt` must pass at `maxIssues: 0`. The no-MockK / no-`verify` rules above are agent-enforced in this repo (Enclave machine-enforces its twins with a custom Detekt rule set Bloviate does not have yet). Work follows `AGENTS.md` §3.7: one MR per Linear sub-issue with `Fixes BLO-xx` in the MR description.
