# Agent Routing Contract — Bloviate

This is the root source of truth for agent-facing repo instructions. Agent-specific
files such as `CLAUDE.md` and `GEMINI.md` should only point here to avoid drift.
Never modify this file or any file in `/contracts/` without an explicit
**"Contract Waiver"** from the user.

Bloviate is a Shippopotamus Labs floating-bubble dictation app for Android
(package `com.shippopotamus.bloviate`): a bubble appears over any focused text
field, the user taps and speaks, and cleaned text is inserted at the cursor.
Transcription is **on-device** (whisper.cpp via the shared `whisper-android`
library, vendored at `third_party/whisper-android` and consumed by composite
build). Design source of truth: `~/workspace/wispr-clone/PLAN.md` (kept outside
this repo).

---

## 1. Contract Routing (Read Before Writing Any Code)

Every task maps to one or more binding contracts in `/contracts/`. Read the relevant contract(s) before generating code or proposing architecture.

| Task type | Contract to read |
|-----------|-----------------|
| Module/package structure, dependency direction, layer responsibilities, navigation, Scaffold, accessibility service, overlay window, foreground service, `WorkManager`, background tasks | `ARCHITECTURE_RULES.md` |
| Kotlin style, naming, Compose stability, modifier conventions, state hoisting, Flow/coroutines, testing conventions, test coverage mandate | `CODE_STYLE_RULES.md` |
| Persistence (DataStore, model files, dictionary, history), persisted-format versioning, threading | `DATA_LAYER_RULES.md` |
| Speech transcription: engine lifecycle, warm context, model integrity, thermal/memory pressure, text cleanup pipeline, processing modes | `TRANSCRIPTION_RULES.md` |
| Colors, typography, Compose component structure, previews, bubble overlay UI states, accessibility | `UI_DESIGN_SPEC.md` |
| JUnit 5, Fakes vs Mocks, Robolectric, Turbine, state-based assertions, test coverage | `UNIT_TESTING.md` |
| **Adding or editing any user-facing copy** — labels, setting descriptions, buttons, empty states, errors, notifications, onboarding, paywall copy, dialog text | `contracts/gospel/WUMBO-GOSPEL.md` (the gospels submodule) — read it *before* writing the copy, not as a cleanup pass after |
| Versioning, release prep, Play release notes, Play internal-track upload, the deploy workflow, release tags | `RELEASE.md` — read it *before* any release work; the derived `versionCode` rules and the release-prep PR gate in there bind every release |

The copy row binds app chrome only. Persona style definitions and their output are **exempt by design** (persona output is character work; see `contracts/gospel/WUMBO-GOSPEL.md`), so persona tasks do not route through the voice contract — but the screens around the personas do.

When a task spans multiple domains, read all relevant contracts.

Before broad edits, first map the relevant subsystem, identify contracts and call
paths, then write a brief implementation plan. For large investigations, use
subagents if the harness supports them so exploration does not flood the main
context.

---

## 2. Repository Map

Bloviate is a **multi-module** Android app with strict layering (see
`contracts/ARCHITECTURE_RULES.md` for the binding layering rules): `:app` plus
the `:core` library modules below, packages mapping to modules one-to-one.

- `core/domain/`: `:core:domain` — domain models, evaluators, domain-only ports, diagnostics. Pure Kotlin/JVM, framework-free, depends on nothing (ENC-159).
- `core/application/`: `:core:application` — use cases, application ports, port-chaining processors. Pure Kotlin/JVM, depends on `:core:domain` only (ENC-159).
- `core/data/`: `:core:data` — implementations of application ports (persistence, downloads, platform adapters, transcription engines). Android library, depends on `:core:domain` + `:core:application` (ENC-169).
- `app/`: the application — shell, DI wiring, launcher activity, app resources.
  Packages under `com.shippopotamus.bloviate`:
  - `presentation/` — Compose UI, ViewModels, presentation models.
  - `service/` — `FieldWatcherService` (accessibility), `BubbleOverlayService` (foreground service + overlay), insertion.
  - `di/` — Hilt modules wiring ports to implementations.
- `contracts/`: binding agent contracts (this file routes to them).
- `config/detekt/`: Detekt static analysis config.
- `third_party/whisper-android/`: **separate repo** (`browntown.development-group/whisper-android`) vendored as a git submodule and consumed via composite build. Do not edit it from this repo's tickets — library changes get their own tickets in that repo.
- `gradle/libs.versions.toml`: version catalog. Versions are aligned with Enclave's catalog on purpose — do not bump one repo's pins without checking the other.

Main toolchain: Gradle Kotlin DSL (wrapper, same version as Enclave), Kotlin /
Compose / Hilt, CMake/Android NDK via the `whisper-android` library only.

---

## 3. Hard Rules (Non-Negotiable)

### 3.1 Clean Architecture — Zero Tolerance

- Ports-and-adapters: `domain/` contains models, ports (`*Port` interfaces), and use cases, and stays free of Android framework, Compose, Hilt, and library-implementation types in its public APIs.
- Dependency direction is inward toward `domain/`. `data/`, `service/`, and `presentation/` may depend on `domain/`; `domain/` must not depend on them.
- Domain models are **never** passed directly to composables. ViewModels map domain → presentation. Presentation models (`*UiState`, `*UiEvent`) must not embed domain types.
- No `NavController` passed into composables. Navigation via pure callbacks hoisted to the route level.
- **ViewModels depend on use cases only** — never on `*Repository`/`*Port` interfaces or `data/` classes directly. Multi-source `Flow` composition lives in a domain use case; write paths go through use cases. (Agent-enforced; Enclave enforces the twin rules with a custom Detekt rule set that Bloviate does not have yet — do not weaken the rule because it is not machine-checked here.)
- The modules are split out of `:app` (`:core:domain`/`:core:application` in ENC-159, `:core:data` in ENC-169); the dependency graph in `contracts/ARCHITECTURE_RULES.md` §1 is binding verbatim.

**Violation response:** Reply with exactly: `"Contract violation detected in [file]. Request explicit 'Contract Waiver' from user."` Then stop. Do not generate code.

### 3.2 Android Code — AndroJack Level 3 Protocol

For every Kotlin, XML, or Gradle code block generated:

1. **Pre-generation:** Call the appropriate `androjack` tool to ground the solution before writing code:
   - Compose UI tasks → `mcp_androjack_material3_expressive`
   - Dependency changes → `mcp_androjack_gradle_dependency_checker`
   - API-level questions → `mcp_androjack_android_api_level_check`
   - New components/APIs → `mcp_androjack_android_component_status`
   - Architecture questions → `mcp_androjack_architecture_reference`

2. **Post-generation:** Pass every generated code block through `mcp_androjack_android_code_validator` (minSdk 34, targetSdk 36).

3. **FAIL verdict:** Fix internally and re-run validator. Never output code that fails validation.

(If the AndroJack MCP is unavailable in the session, say so, proceed with Gradle/tests, and note that validation was skipped — the same fallback Enclave uses for its Studio bridge.)

### 3.3 Test Coverage — Mandatory

Whenever production code is added or modified (new classes, public functions, logic in `domain/`, `data/`, `service/`, ViewModels, repositories, mappers, use cases):

- Write or extend unit tests in the matching `src/test/kotlin` source set using JUnit 5 + Turbine (for Flow) + **hand-written fakes** for Bloviate-owned ports. Bloviate has no shared `:core:testing` module yet: fakes live in the module's test source set; when a shared testing module is created, a new port's fake moves there in the same MR as the port.
- MockK is allowed only for third-party SDK boundaries with an explicit `@Suppress` and one-line justification. Never `verify`/`coVerify` in ViewModel tests — assert on `UiState` (see `contracts/UNIT_TESTING.md`).
- Tests must pass before `TASK_STATUS: COMPLETE`.
- Trivial styling-only or preview-only changes are exempt (theme/preview matrix changes are not exempt — see `contracts/UI_DESIGN_SPEC.md`).

### 3.4 Schema Lock

**Never** invent persisted state for transient UI state (e.g., `is_listening`, `is_loading`). Persisted formats — DataStore keys, on-disk model manifests, dictionary and history file formats — are schema: changes require explicit developer authorization, are **versioned**, and ship with a migration or a documented invalidation path plus tests (adapted from Stratum §4.5).

### 3.5 Transcription — On-Device First, No On-Device LLM

- Phase 1 transcription is 100% on-device via the `whisper-android` library. No audio or transcript leaves the device.
- **No on-device LLM cleanup, in any phase** (user decision, 2026-10-17: too slow at the stop→text moment). Cleanup is the deterministic rule-based `TextPostProcessor` (`contracts/TRANSCRIPTION_RULES.md`).
- Cloud processing, if ever built, is opt-in, off by default, API-based (no self-hosted GPU), and goes behind the `TranscriptionEngine` / `TextPostProcessor` seams. **Never ship a provider API key in the APK**; the key-distribution design must be solved and approved before any cloud code lands.

### 3.6 Distribution Scope — Sideload First, Play per the Release Contract

Bloviate was distributed by sideload while the Play decision was deferred. That decision is now taken up as a planned project (P-BLO-9, the Play launch sequence), and the release machinery for it landed in BLO-173.

- Play work follows `contracts/RELEASE.md`: derived `versionCode`, the release-prep PR gate, and a `workflow_dispatch`-only internal-track deploy that never writes to `main`. Read that contract before touching versioning, release notes, or the deploy workflow.
- Do **not** add Play Console, Play App Signing, bundle-for-Play, or store-release automation beyond that contract unasked.
- Play publication still carries heavy policy scrutiny of the AccessibilityService + overlay combination; that is the launch project's problem (BLO-151), not a drive-by change.
- Linear `Deployed` / `Released` states (§3.7) therefore mean manual milestones on the **parent issue only**.

### 3.7 Linear Issue Tracking — Chunk-Based Trunk Workflow

Single source of truth for work items is Linear. Bloviate has its own **Bloviate** team (key `BLO`, workspace Browntown Development: https://linear.app/browntown-development/team/BLO/all) with **project P-BLO-8 "Bloviate"**, created 2026-10-23 after Sean moved Linear to a paid plan. New issue IDs are therefore `BLO-*`. Do not use GitLab issues.

- **Legacy tickets:** work filed before the move lives in the **Enclave** team (key `ENC`) as **project P-ENC-7 "Bloviate"** — the free plan's two-team limit forced that arrangement. The `ENC-*` IDs referenced throughout this repo (ENC-46…ENC-50 phase parents, ENC-57, ENC-159, ENC-169, ENC-197, …) are those legacy tickets and stay valid for them. File all new work in `BLO`.
- **Chunk-based development:** one parent issue per phase/feature (ENC-46…ENC-50 are the original phase parents, in the legacy ENC project); each chunk of work is a **sub-issue**; **one MR per sub-issue** — never batch multiple chunks into one MR.
- **Ticket first:** ensure you have a Linear ticket to work from via the Linear MCP (`save_issue`, `get_issue`, `list_issues`; see §7). If you do not know the ticket, ask the user. If told it does not exist, create one under the right parent with a `type::` label (`type::feature`, `type::bug`, `type::chore`, `type::polish`). Once you have the ticket, put it **In Progress** (`save_issue` with `state: "In Progress"`), then start working.
- **Branch naming:** copy the git branch name from the Linear sub-issue (it contains the issue ID, e.g. `browntowndevelopment/blo-57-bloviate-<slug>`) — this auto-links the branch.
- **Every MR description MUST contain a closing magic word + the sub-issue ID**, e.g. `Fixes BLO-57`. Linear's MR automations move the issue to **In Review** when the MR opens and to **Merged** when the MR merges. A merge is NOT a deploy. `In Progress` is manual: the agent sets it when work starts. (Exception: a brand-new repo's bootstrap commit goes straight to `main` — there is nothing to MR against — and the agent sets the ticket to `Merged` manually after push.)
- To link an MR without triggering the automatic status moves, use `Refs BLO-57` or `Contributes to BLO-57` instead of a closing magic word.
- Commit messages and MR comments do **not** link issues — only branch name, MR title, and MR description.
- **Merge vs deploy vs release:** `Merged` = merged to main (trunk). `Deployed` / `Partially Deployed` / `Released` live on the **parent issue only** and are manual (developer sideloads, then updates Linear or asks the agent to). Sub-issues stop at `Merged`.
- Never close the parent manually while sub-issues are open.

---

## 4. Scoped Commands

Prefer the narrowest command that covers the changed area.

- App compile: `./gradlew :app:assembleDebug`
- App unit tests: `./gradlew :app:testDebugUnitTest`
- Full unit tests: `./gradlew testDebugUnitTest`
- Style check: `./gradlew ktlintCheck` (advisory — `ignoreFailures` is on, as in Enclave)
- Static analysis: `./gradlew detekt` (**required** — `maxIssues: 0`; `assemble*` tasks already depend on `detekt`)
- KSP/Hilt smoke: `./gradlew :app:kspDebugKotlin`

Before merge or a large cross-module change, run the CI-aligned checks:
`./gradlew ktlintCheck`, `./gradlew detekt`, `./gradlew :app:assembleDebug`, `./gradlew testDebugUnitTest`.

**Composite-build gotcha:** the included `whisper-android` build resolves the SDK location per build — when building from an IDE/shell without `ANDROID_HOME` in the environment, it needs its own `local.properties` (`cp local.properties third_party/whisper-android/local.properties`), and NDK 28.2.13676358 must be installed. See `README.md`.

---

## 5. Context Noise And Generated Code

Do not inspect or edit these during normal tasks unless the task explicitly targets them:

- Gradle/build outputs: `build/`, `**/build/`, `.gradle/`, `.kotlin/`, `.cxx/`, `.externalNativeBuild/`.
- Vendored/library sources: `third_party/whisper-android/` (separate repo — see §2) and its nested `third_party/whisper.cpp/`.
- Binary/model noise: `*.so`, `*.bin` (Whisper model files), APK/AAB outputs, keystores.

---

## 6. Code Intelligence Setup

- Android/Kotlin: import the root Gradle project in **Android Studio** (or IntelliJ). Use JDK 17 with the checked-in Gradle wrapper (the Gradle daemon itself runs on a JDK 21 toolchain per `gradle/gradle-daemon-jvm.properties`, same as Enclave).
- **Android CLI:** `android` 1.0+ on PATH (`android info`) when available.

---

## 7. Tool Priority (MCP and Android CLI)

Before any Android/Kotlin task, check MCP tools **and** whether Android Studio bridge commands are available.

| MCP Server / tool | When to use |
|------------|-------------|
| `androjack` | All Android/Kotlin validation, API research, dependency checks — **mandatory for all code generation** (§3.2) |
| `mobile-mcp` | Install APKs, interact with a running emulator/device, take screenshots |
| `google-developer-knowledge` | Official Android/Google API documentation lookup |
| `linear` (Linear MCP) | **All work-item tracking for Bloviate (team `BLO`; legacy tickets under team `ENC`, project P-ENC-7)** — `list_issues`/`get_issue` to find tickets, `save_issue` to create/update (including `In Progress`), `save_comment` for MR notes. Use before starting work, not GitLab issues |

### Android CLI and Studio bridge (shell)

| Capability | Command family | When to use |
|------------|----------------|-------------|
| Studio connected | `android studio check` | Start of Compose UI, refactor, or navigation tasks |
| Symbol navigation | `android studio find-declaration`, `find-usages` | Cross-module Kotlin references (prefer over blind grep) |
| IDE inspections | `android studio analyze-file` | Before/after editing a Kotlin/Compose file |
| Resolved versions | `android studio version-lookup` | Dependency bumps, version skew debugging |
| Compose previews | `android studio render-compose-preview` | Visual check of `@Preview` composables — **requires debug build** |
| APK / deploy | `android describe`, `android run` | After `./gradlew :app:assembleDebug` |
| Device UI tree | `android layout` | Layout/debug on connected device or emulator |
| Android docs | `android docs search`, `android docs fetch` | Authoritative KB lookups |

**Project identifier:** `--project="Bloviate"` when multiple projects could be open.

**AndroJack vs Android CLI:** AndroJack validates **generated** code (§3.2). Android CLI queries the **running IDE** and devices. Use both; neither replaces the other.

If `android studio check` reports no running Studio, continue with Gradle/tests and note that bridge commands were skipped.

---

## 8. Skills, Hooks, And Deterministic Checks

Keep reusable expertise out of root instructions. Good candidates for on-demand skills or skill-like docs in this repo:

- Accessibility watcher / overlay work against `contracts/ARCHITECTURE_RULES.md` + `contracts/TRANSCRIPTION_RULES.md`.
- Model-download work (the Enclave download-stack port) against `contracts/DATA_LAYER_RULES.md`.
- Pre-merge review using the CI-aligned Gradle checks in §4.

Use scripts or hooks for deterministic behavior. Existing deterministic checks are Gradle tasks (`detekt`, `ktlintCheck`, unit tests). Do not invent hook config unless the repo adds checked-in hook infrastructure.

---

## 9. Executable Validation Before Completion

Before declaring `TASK_STATUS: COMPLETE`, run the narrowest relevant validations:

- **App compilation:** `./gradlew :app:assembleDebug`
- **DI/KSP changes:** `./gradlew :app:kspDebugKotlin`
- **Tests:** `./gradlew testDebugUnitTest`
- **Style:** `./gradlew ktlintCheck`
- **Static analysis:** `./gradlew detekt` (required — `maxIssues: 0`; must pass before completion)

Detekt runs per module and `assemble*` tasks depend on it; still run `./gradlew detekt` explicitly after Kotlin changes — do not rely on compile-only checks.

---

## 10. Maintenance

Repo maintainers own this file. Review every 3–6 months:

- Root and local agent instructions.
- `/contracts` accuracy against current package paths and Gradle commands.
- Skills or skill-like workflow docs.
- Ignore rules in `.gitignore` and IDE settings.
- LSP/code-intelligence setup.
- MCP/plugin notes.
- Stale model-specific workarounds in active tool-specific rules.

---

## 11. Autonomous Continuation Protocol

After any file edit is accepted or tool result returns:
- Continue to the next logical step without pausing for confirmation.
- End every response with exactly one of:
  - `TASK_STATUS: COMPLETE — Phase objectives achieved.`
  - `TASK_STATUS: PARTIAL — Completed: [list]. Remaining: [list]. Continuing.`

## PR titles and commit messages

PR titles follow Conventional Commits, because squash merging makes the
PR title the commit subject on main - and Forge's ticket cascade reads
titles when linking a PR to its ticket.

Format:

    type(scope): imperative subject (KEY-123)

- type - feat, fix, polish, chore, docs, refactor, test, ci, build,
  perf, style. Types mirror the Linear type labels (feature -> feat,
  bug -> fix).
- scope - the area the change touches, lowercase: a module, layer, or
  surface.
- subject - imperative mood, lowercase, no trailing period:
  "add linear progression", not "adds" or "added".
- (KEY-123) - the Linear ticket key in parentheses at the end, when
  the work has a ticket. Branch names stay the primary ticket link
  (name the branch after the key); the title key is the backup the
  cascade reads second.

Examples:

    feat(styles): add Socrates style (BLO-157)
    fix(transcription): keep partial results on engine restart (BLO-163)

A CI check (pr-title) enforces the shape on every PR. Individual
commit messages on a branch are NOT linted - write them for the
humans and agents reading the branch, and let the PR title carry
the standard.
