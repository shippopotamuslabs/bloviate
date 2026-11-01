# DATA_LAYER_RULES.md
**Bloviate – Data Layer Contract v1.0**
(Adapted from Enclave `DATA_LAYER_RULES.md` v2.4 + Stratum's versioned-schema rule. Bloviate has no Room database and no server sync; persistence is DataStore + app-private files.)

Rules for the `data/` package: DataStore preferences, model files and manifests, personal dictionary, dictation history, WorkManager downloads, and mapping. Always enforce together with `ARCHITECTURE_RULES.md`.

> Canon vocabulary (GLO-20): `data/` is this repo's legacy name for the
> infrastructure layer — persistence is a package inside infrastructure
> (`contracts/gospel/ARCHITECTURE-GOSPEL.md` §6). The file and package
> names stay as they are in this repo until the restructure phase.

---

## Strict Boundaries

| Always | Never |
|--------|-------|
| Keep all persistence types (DataStore keys, file formats, entities) inside `data/`. | Expose persistence types or annotations to `presentation/`, `service/`, or `domain/`. |
| Version every persisted format and migrate or invalidate explicitly. | Silently change a persisted format's meaning under the same key/version. |
| Run heavy file/hash work with `withContext(Dispatchers.IO)`. | Do file IO, hashing, or model parsing on the main thread. |
| Store timestamps as `Long` (epoch millis). | Persist transient UI state (`is_listening`, `is_loading`, bubble visibility). |
| Verify model files by SHA-256 after download and before first load. | Load a model file whose hash has not been verified. |
| Map persistence ↔ domain strictly inside `data/` with pure mapper functions. | Return persistence models from repositories/ports. |

## 1. Persistence Inventory (v1)

- **Preferences/settings:** DataStore (preferences) — processing mode, model choice, language, cleanup toggles, bubble position/size.
- **Model files:** whisper.cpp ggml binaries in app-private storage, delivered by the model-download stack (a port of Enclave's battle-tested WorkManager download design — worker chain, byte-offset resume, hash verification, atomic finalize). Model binaries are **never committed to git**.
- **Personal dictionary:** user replacement map (spoken form → replacement), stored as a versioned file/DataStore entry in `data/`.
- **Dictation history:** local-only, on-device, user-clearable. Never leaves the device in Phase 1.

If a Room database is ever introduced, it lands in `data/` behind application ports and Enclave's `DATA_LAYER_RULES.md` (entities, DAOs, repository pattern, schema lock) becomes binding for it — that adoption requires its own ticket.

## 2. Schema Lock & Versioning

- **SCHEMA LOCK:** Never invent persisted keys/columns for transient presentation state. Transient state belongs in the UI/service layer.
- Persisted-format changes require explicit developer authorization.
- Every persisted format carries a version. Changes are either (a) additive and backward-compatible, (b) migrated by tested code, or (c) explicitly invalidated (old data discarded by design, documented in the MR). Silent reinterpretation is forbidden.
- Timestamps are `Long` epoch millis everywhere in persistence; formatting happens exclusively in the presentation layer.

## 3. Repository Pattern, Mapping & Threading

- Repositories/stores implement `application/` port interfaces (`Repository` survives only as an infrastructure implementation-class name, never as interface vocabulary — see `contracts/gospel/ARCHITECTURE-GOSPEL.md` §3.3, §4); data sources (DataStore, files, WorkManager) are injected via constructor.
- **Mapping boundaries:** persistence ↔ domain mapping happens entirely in `data/` (repository or dedicated mapper functions). Mappers are pure functions. Domain → presentation mapping happens in ViewModels.
- **Threading boundaries:** all blocking file IO and hashing is `suspend` + `Dispatchers.IO`. Downloads expose progress as `Flow` from WorkManager state — no callbacks (see `ARCHITECTURE_RULES.md` §0).

## 4. Model Download Rules

- Downloads are unique work (`ExistingWorkPolicy.KEEP`/`REPLACE`), resumable from byte offsets, and finalized atomically: download to a temp file, verify SHA-256, then rename into place. A partial or unverified file must never be visible to the transcription engine.
- Free-space is checked before starting a download (the default model is ~142 MB; larger models ~488 MB+).
- Download state survives process death via WorkManager persistence alone — no in-memory singletons as the source of truth.

## 5. Executable Validation (Agent MUST run before TASK_STATUS: COMPLETE)

1. **Compile:** Run `./gradlew :app:assembleDebug`.
2. **Tests:** Run `./gradlew :app:testDebugUnitTest` for the changed stores/repositories, including migration/invalidation tests for any persisted-format change.
3. **Check Style:** Run `./gradlew detekt`.
