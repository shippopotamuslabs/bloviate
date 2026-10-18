# TRANSCRIPTION_RULES.md
**Bloviate – Transcription Layer Contract v1.0**
(Adapted from the transferable core of Enclave `INFERENCE_RULES.md` v2.6 — lifecycle, integrity, thermal/memory discipline — plus Bloviate's plan decisions. Enclave's LLM-specific rules do not apply here: Bloviate runs no LLM on device.)

Non-negotiable rules for everything that turns audio into inserted text: the whisper engine, audio capture, the cleanup pipeline, and any future transcription engine. Always enforce together with `ARCHITECTURE_RULES.md`.

---

## Strict Boundaries

| Always | Never |
| :--- | :--- |
| Transcribe **on-device** by default (Phase 1: on-device only). | Send audio or transcripts off-device without the user's explicit opt-in to cloud mode. |
| Access transcription through the domain seams (`TranscriptionEngine`, `TextPostProcessor`). | Call `WhisperEngine`, JNI, or any engine implementation directly from `presentation/` or `service/`. |
| Keep the engine context **warm** in `BubbleOverlayService` while dictation is enabled. | Init/close the whisper context per utterance (Enclave's chat pattern — wrong for a 30×/day bubble). |
| Serialize engine calls behind a `Mutex`. | Call the native engine concurrently. |
| Verify the model file's SHA-256 after download and before first load. | Load an unverified or partially-downloaded model file. |
| Watch thermal state via `PowerManager` and memory via `onTrimMemory`. | Rely on `BatteryManager` or JVM heap metrics for pressure decisions. |
| Degrade visibly but safely: any pipeline failure leaves the user's existing text untouched. | Fail silently in a way that loses the user's audio without a retry path. |

## 1. Core Constraints

- **On-device priority:** default operations proceed entirely on-device. No network use except (a) model downloads and (b) future opt-in cloud mode (§6).
- **Runtime:** whisper.cpp via the shared `whisper-android` library (`WhisperEngine` API). The library is a separate repo — changes to it get their own tickets there, never drive-by edits through the submodule.
- **No on-device LLM cleanup — ever** (user decision, 2026-10-17). Cleanup is deterministic (§5). Do not propose llama.cpp/LLM post-processing on the phone.
- **Target:** Android 14+ (minSdk 34), arm64-v8a.

## 2. Pipeline (Phase 1)

1. Press → audio capture starts immediately: 16 kHz mono PCM16 normalized to `FloatArray` (the library's `AudioRecorder`, same lineage as Enclave's capture).
2. **No auto-stop on pauses.** Stop is user-driven (tap / hold-release). Hard cap ~3 minutes. (Do **not** port Enclave's 10-second silence finalization — it is right for chat, wrong for dictation.)
3. Stop → trim leading/trailing silence (energy threshold; Enclave's 0.015 amplitude / 0.02 RMS values are the starting point) → transcribe the whole buffered utterance in **one pass**. (Chunked/streaming transcription into the final text is forbidden: Whisper's 30-second-window architecture makes chunked output markedly worse. Provisional re-transcription for *display only* is a future option, never the inserted text.)
4. Transcript → `TextPostProcessor` (§5) → insertion (paste-first, `ARCHITECTURE_RULES.md` §5.3).
5. Failure at any step leaves existing text untouched and keeps the audio buffer long enough for one retry tap.

## 3. Engine Lifecycle

- **Warm context:** load once when dictation is enabled; reload only on model change or memory pressure. Cold-load latency of the default model is a measured Phase metric, not a per-utterance cost.
- **Lazy, guarded load:** first load is lazy and guarded by a `Mutex` + `@Volatile` (pattern carried from Enclave's auxiliary-model lane).
- **Threads:** engine thread count follows the library default (processors/2, capped at 4) unless on-device measurement justifies a change.
- **Language/params:** pass language and translate flags through the library's `WhisperParams`; never fork the JNI layer to hardcode them.

## 4. System Watchdog (Android 14+)

### Thermal Management
- **Source:** `PowerManager.getCurrentThermalStatus()` and `PowerManager.getThermalHeadroom(30)`.
- **Behavior:** sustained `THERMAL_STATUS_SEVERE` or worse → surface a brief "device warm" state on the bubble and let the current dictation finish; never start a model reload while thermal status is severe.

### Memory Management
- **Native Monitoring:** `BubbleOverlayService` (and the Application) implement `ComponentCallbacks2.onTrimMemory()`.
- **Trimming Logic:**
  - **`TRIM_MEMORY_RUNNING_LOW`:** release cached non-essential buffers (waveform history).
  - **`TRIM_MEMORY_RUNNING_CRITICAL`:** `close()` the engine context to avoid LMK termination; reload lazily on next dictation.

## 5. Text Cleanup (Deterministic, Zero-Model)

The Phase 1 `TextPostProcessor` is pure, deterministic, and unit-tested. It may only:
- Keep Whisper's own punctuation/capitalization.
- Strip standalone filler tokens (`um`, `uh`, `er`) — only when non-grammatical.
- Resolve spoken punctuation commands: "period", "comma", "question mark", "exclamation point", "new line", "new paragraph".
- Repair sentence case, collapse whitespace, complete a trailing period.
- Apply the personal dictionary as post-replacement (e.g. "stratham" → "Stratum").

Accepted Phase 1 limits (do not "fix" them with heuristics that guess): no self-correction resolution ("Tuesday — no, Wednesday" stays literal), no context-aware reformatting.

Whisper `initial_prompt` biasing for dictionary terms is a Phase 2 candidate **only if** the library API grows support for it — it is not a reason to fork the library.

## 6. Processing Modes & Future Engines

- Settings exposes `ProcessingMode.ON_DEVICE` in Phase 1; the enum/UI slot for cloud is reserved, not built.
- **Parakeet (ENC-54):** NVIDIA Parakeet TDT 0.6B v3 via sherpa-onnx is the planned second on-device engine behind the same `TranscriptionEngine` seam — recommended default download when it lands; whisper remains the engine for languages outside Parakeet's set. Engine choice is a model-manager decision, never a call-site branch.
- **Cloud mode (future, opt-in, off by default):** API-based (hosted Whisper + cheap LLM cleanup; no self-hosted GPU). The UI must state plainly that enabling it sends audio/text off-device. **Never ship a provider API key in the APK** — key distribution must be designed and approved first (rule carried from Stratum `AGENTS.md` §4.6).

## 7. Executable Validation
1. **Compile:** Run `./gradlew :app:assembleDebug`. Must pass.
2. **Tests:** Cleanup and pipeline logic is pure/deterministic — every rule in §5 ships with unit tests (`./gradlew :app:testDebugUnitTest`).
3. **On-device check:** any change to capture, engine lifecycle, or insertion must be verified by dictating on a real device (Pixel + Razr Fold) before the ticket is called done; stop→insert latency is measured, not assumed.
