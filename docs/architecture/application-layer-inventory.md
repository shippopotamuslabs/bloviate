# Application-Layer Inventory (ENC-158)

Generated from real call sites (import traces) at the ENC-159 split. Doctrine: a port lives in the layer that consumes it; the sole exception is a port consumed only by a domain service/evaluator, which stays in `:core:domain`. Implementations stay in `:app` `data/` (uniformly implementing application ports) unless noted. Package-split + Detekt fallback was NOT used — no blocker was hit (see Fallback).

## Explicit borderline rulings

- **(a) `StylingTextPostProcessor` + `LlmStylePort`:** `StylingTextPostProcessor` chains `TextPostProcessor` + `LlmStylePort` (network) + `ApiKeyPort` + `SendProgressPort` + `StylePreferencePort` — I/O-shaped port-chaining = application orchestration. It moves to `application.evaluator`; `LlmStylePort` moves with it to `application.port`. The domain-only exception does **not** apply (consumers are application + `:app` service/`data`, not domain-only).

- **(b) `StyleAwareTextPostProcessor` / `DictionaryAwareTextPostProcessor`:** each reads a live preference/dictionary `Flow` (`StylePreferencePort` / `DictionaryPort`) at send time. That is orchestration over application ports, not pure transformation. Both move to `application.evaluator`, and `StylePreferencePort`/`DictionaryPort` move to `application.port` (also consumed by use cases). Their pure delegates (`BaselineTextPostProcessor`, `DictionaryCorrector`, `LowercaseCasualTransformer`) stay in `domain.evaluator`.

- **(c) `TextPostProcessor` seam:** the interface **stays in `:core:domain`** (`domain.port`). It is the `TRANSCRIPTION_RULES` pipeline seam: implemented by domain processors (`BaselineTextPostProcessor` in domain, plus the application chain implementing it) and consumed by the `:app` service layer via Hilt (`VoiceModule.bindTextPostProcessor`). Moving it to application would force `BaselineTextPostProcessor` (pure domain) to depend upward on application — illegal. Domain owning the seam lets application implement/chain it (`application -> domain`) and `:app` consume it.

- **(d) Service-only ports** (`AudioCapturePort`, `RecordingSessionPort`, `TextInsertionPort`, `TranscriptSinkPort`, `ShadePresencePort`, `ActiveModelStorePort`, `TranscriptionEngine`): no use-case consumer, only `:app` service/`data`/DI. Default ruling applied: **application**. `:app` depends on application+domain, `data/` implements application ports uniformly; service-only does not make a port domain (domain must not gain I/O-shaped contracts it never consumes).

- **(e) `CadenceCounters`:** pure `object` of `AtomicInteger/AtomicLong` counters, no I/O, consumed by `:app` `data/RecordingSessionManager` + `service/*` for ENC-69 diagnostics. **Stays in `:core:domain`** (`domain.diagnostics`); `:app` already depends on domain. No port involved.

- **`ObserveStyleCatalogUseCase` (pure-logic candidate):** takes no port, returns `StyleCatalog.ALL`. Named as the only use case that looks like pure domain logic. Ruling: **stays in application** — it is a ViewModel-facing entry point (`StyleViewModel`); the pure data (`StyleCatalog`) itself stays in domain, so no domain logic is stranded in application beyond a one-line accessor, and splitting it would create a domain use-case layer the new graph deliberately removes.


## Use cases (35 -> `:core:application` `application.usecase`)

| File | Port/evaluator deps (traced) | Destination | Named consumers (traced import sites) |
|---|---|---|---|
| AddDictionaryCorrectionUseCase.kt | DictionaryPort | application | app:presentation/DictionaryViewModel.kt; app-test:presentation/DictionaryViewModelTest.kt |
| AddDictionaryWordUseCase.kt | DictionaryPort | application | app:presentation/DictionaryViewModel.kt; app-test:presentation/DictionaryViewModelTest.kt |
| ClearApiKeyUseCase.kt | ApiKeyPort | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| EnsureModelUseCase.kt | ModelStorePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| EnsureParakeetModelUseCase.kt | ParakeetModelStorePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| IsBatteryOptimizationExemptUseCase.kt | BatteryExemptionPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| IsFieldWatcherEnabledUseCase.kt | WatcherAccessPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| IsMicPermissionGrantedUseCase.kt | MicPermissionPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| IsOverlayPermissionGrantedUseCase.kt | BubbleOverlayAccessPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveBubbleAppearanceUseCase.kt | BubbleAppearancePort | application | app:presentation/MainViewModel.kt; app:service/BubbleOverlayService.kt; app-test:presentation/MainViewModelTest.kt |
| ObserveBubblePositionUseCase.kt | BubblePositionPort | application | app:service/BubbleOverlayService.kt |
| ObserveBubbleServiceRunningUseCase.kt | BubbleServiceStatePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveBubbleVisibilityUseCase.kt | BubbleVisibilityReducer, FieldFocusPort, KeyboardVisibilityPort | application | app:service/BubbleOverlayService.kt |
| ObserveDictionaryUseCase.kt | DictionaryPort | application | app:presentation/DictionaryViewModel.kt; app-test:presentation/DictionaryViewModelTest.kt |
| ObserveEnginePreferenceUseCase.kt | EnginePreferencePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveHasApiKeyUseCase.kt | ApiKeyPort | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| ObserveModelStateUseCase.kt | ModelStorePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveOnboardingProgressUseCase.kt | OnboardingPort | application | app:presentation/OnboardingViewModel.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveParakeetModelStateUseCase.kt | ParakeetModelStorePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| ObserveResidentPresenceUseCase.kt | ResidentPresencePort | application | app:presentation/MainViewModel.kt; app-test:presentation/MainViewModelTest.kt |
| ObserveSelectedStyleUseCase.kt | StylePreferencePort | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| ObserveStyleCatalogUseCase.kt | — | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| ObserveWatcherConnectionUseCase.kt | WatcherConnectionPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| RecordBubbleUserStartUseCase.kt | ResidentPresencePort | application | app:presentation/MainViewModel.kt; app-test:presentation/MainViewModelTest.kt |
| RecordBubbleUserStopUseCase.kt | ResidentPresencePort | application | app:presentation/MainViewModel.kt; app-test:presentation/MainViewModelTest.kt |
| RefreshMicPermissionUseCase.kt | MicPermissionPort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| RemoveDictionaryCorrectionUseCase.kt | DictionaryPort | application | app:presentation/DictionaryViewModel.kt; app-test:presentation/DictionaryViewModelTest.kt |
| RemoveDictionaryWordUseCase.kt | DictionaryPort | application | app:presentation/DictionaryViewModel.kt; app-test:presentation/DictionaryViewModelTest.kt |
| SaveApiKeyUseCase.kt | ApiKeyPort | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| SaveBubbleAppearanceUseCase.kt | BubbleAppearancePort | application | app:presentation/MainViewModel.kt; app-test:presentation/MainViewModelTest.kt |
| SaveBubblePositionUseCase.kt | BubblePositionPort | application | app:service/BubbleOverlayService.kt |
| SaveOnboardingProgressUseCase.kt | OnboardingPort | application | app:presentation/OnboardingViewModel.kt; app-test:presentation/OnboardingViewModelTest.kt |
| SelectEngineUseCase.kt | EnginePreferencePort | application | app:presentation/MainViewModel.kt; app:presentation/OnboardingViewModel.kt; app-test:presentation/MainViewModelTest.kt; app-test:presentation/OnboardingViewModelTest.kt |
| SelectStyleUseCase.kt | StylePreferencePort | application | app:presentation/StyleViewModel.kt; app-test:presentation/StyleViewModelTest.kt |
| SetAutoStartEnabledUseCase.kt | ResidentPresencePort | application | app:presentation/MainViewModel.kt; app-test:presentation/MainViewModelTest.kt |

## Ports (28 total: 27 -> application, 1 stays domain)

| Port | Destination | Named consumers (traced) |
|---|---|---|
| ActiveModelStorePort | application (`application.port`) | app:data/RecordingSessionManager.kt; app:data/SelectedModelStore.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app-test:testing/FakeActiveModelStorePort.kt |
| ApiKeyPort | application (`application.port`) | app:data/EncryptedApiKeyStore.kt; app:data/OpenRouterStyleClient.kt; app:di/VoiceModule.kt; app-test:testing/FakeApiKeyPort.kt; core/application:application/evaluator/StylingTextPostProcessor.kt; core/application:application/usecase/ClearApiKeyUseCase.kt; core/application:application/usecase/ObserveHasApiKeyUseCase.kt; core/application:application/usecase/SaveApiKeyUseCase.kt; core/application-test:testing/FakeApiKeyPort.kt |
| AudioCapturePort | application (`application.port`) | app:data/LibraryAudioCapture.kt; app:data/RecordingSessionManager.kt; app:di/VoiceModule.kt; app-test:testing/FakeAudioCapturePort.kt |
| BatteryExemptionPort | application (`application.port`) | app:data/AndroidBatteryExemptionAccess.kt; app:di/FieldWatcherModule.kt; app-test:testing/FakeBatteryExemptionPort.kt; core/application:application/usecase/IsBatteryOptimizationExemptUseCase.kt |
| BubbleAppearancePort | application (`application.port`) | app:data/DataStoreBubbleAppearanceStore.kt; app:di/BubbleModule.kt; app-test:testing/FakeBubbleAppearancePort.kt; core/application:application/usecase/ObserveBubbleAppearanceUseCase.kt; core/application:application/usecase/SaveBubbleAppearanceUseCase.kt |
| BubbleOverlayAccessPort | application (`application.port`) | app:data/AndroidBubbleOverlayAccess.kt; app:di/BubbleModule.kt; app:service/BubbleOverlayService.kt; app-test:testing/FakeBubbleOverlayAccessPort.kt; core/application:application/usecase/IsOverlayPermissionGrantedUseCase.kt |
| BubblePositionPort | application (`application.port`) | app:data/DataStoreBubblePositionStore.kt; app:di/BubbleModule.kt; app-test:testing/FakeBubblePositionPort.kt; core/application:application/usecase/ObserveBubblePositionUseCase.kt; core/application:application/usecase/SaveBubblePositionUseCase.kt |
| BubbleServiceStatePort | application (`application.port`) | app:data/BubbleServiceStateHolder.kt; app:di/BubbleModule.kt; app:service/BubbleOverlayService.kt; app-test:testing/FakeBubbleServiceStatePort.kt; core/application:application/usecase/ObserveBubbleServiceRunningUseCase.kt |
| DictionaryPort | application (`application.port`) | app:data/DataStoreDictionaryStore.kt; app:di/VoiceModule.kt; app-test:testing/FakeDictionaryPort.kt; core/application:application/evaluator/DictionaryAwareTextPostProcessor.kt; core/application:application/usecase/AddDictionaryCorrectionUseCase.kt; core/application:application/usecase/AddDictionaryWordUseCase.kt; core/application:application/usecase/ObserveDictionaryUseCase.kt; core/application:application/usecase/RemoveDictionaryCorrectionUseCase.kt; core/application:application/usecase/RemoveDictionaryWordUseCase.kt; core/application-test:testing/FakeDictionaryPort.kt |
| EnginePreferencePort | application (`application.port`) | app:data/DataStoreEnginePreferenceStore.kt; app:data/SelectedModelStore.kt; app:data/SelectingTranscriptionEngine.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app-test:testing/FakeEnginePreferencePort.kt; core/application:application/usecase/ObserveEnginePreferenceUseCase.kt; core/application:application/usecase/SelectEngineUseCase.kt |
| FieldFocusPort | application (`application.port`) | app:data/FieldFocusStateHolder.kt; app:di/FieldWatcherModule.kt; app:service/FieldWatcherService.kt; app-test:testing/FakeFieldFocusPort.kt; core/application:application/usecase/ObserveBubbleVisibilityUseCase.kt; core/application-test:testing/FakeFieldFocusPort.kt |
| KeyboardVisibilityPort | application (`application.port`) | app:data/KeyboardVisibilityStateHolder.kt; app:di/FieldWatcherModule.kt; app:service/FieldWatcherService.kt; app-test:testing/FakeKeyboardVisibilityPort.kt; core/application:application/usecase/ObserveBubbleVisibilityUseCase.kt; core/application-test:testing/FakeKeyboardVisibilityPort.kt |
| LlmStylePort | application (`application.port`) | app:data/OpenRouterStyleClient.kt; app:di/VoiceModule.kt; app-test:testing/FakeLlmStylePort.kt; core/application:application/evaluator/StylingTextPostProcessor.kt; core/application-test:testing/FakeLlmStylePort.kt |
| MicPermissionPort | application (`application.port`) | app:data/AndroidMicPermissionAccess.kt; app:data/RecordingSessionManager.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app-test:testing/FakeMicPermissionPort.kt; core/application:application/usecase/IsMicPermissionGrantedUseCase.kt; core/application:application/usecase/RefreshMicPermissionUseCase.kt |
| ModelStorePort | application (`application.port`) | app:data/OnDeviceWhisperTranscriptionEngine.kt; app:data/SelectedModelStore.kt; app:data/SelectingTranscriptionEngine.kt; app:data/WhisperModelStore.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app-test:testing/FakeModelStorePort.kt; core/application:application/usecase/EnsureModelUseCase.kt; core/application:application/usecase/ObserveModelStateUseCase.kt |
| OnboardingPort | application (`application.port`) | app:data/DataStoreOnboardingStore.kt; app:di/BubbleModule.kt; app:service/BootStartReceiver.kt; app-test:testing/FakeOnboardingPort.kt; core/application:application/usecase/ObserveOnboardingProgressUseCase.kt; core/application:application/usecase/SaveOnboardingProgressUseCase.kt |
| ParakeetModelStorePort | application (`application.port`) | app:data/ParakeetModelStore.kt; app:data/ParakeetTranscriptionEngine.kt; app:data/SelectedModelStore.kt; app:data/SelectingTranscriptionEngine.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app-test:testing/FakeParakeetModelStorePort.kt; core/application:application/usecase/EnsureParakeetModelUseCase.kt; core/application:application/usecase/ObserveParakeetModelStateUseCase.kt |
| RecordingSessionPort | application (`application.port`) | app:data/RecordingSessionManager.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app-test:testing/FakeRecordingSessionPort.kt |
| ResidentPresencePort | application (`application.port`) | app:data/DataStoreResidentPresenceStore.kt; app:di/BubbleModule.kt; app:service/BootStartReceiver.kt; app:service/BubbleOverlayService.kt; app-test:testing/FakeResidentPresencePort.kt; core/application:application/usecase/ObserveResidentPresenceUseCase.kt; core/application:application/usecase/RecordBubbleUserStartUseCase.kt; core/application:application/usecase/RecordBubbleUserStopUseCase.kt; core/application:application/usecase/SetAutoStartEnabledUseCase.kt |
| SendProgressPort | application (`application.port`) | app:service/BubbleRecordingController.kt; app-test:testing/FakeSendProgressPort.kt; core/application:application/evaluator/StylingTextPostProcessor.kt; core/application-test:testing/FakeSendProgressPort.kt |
| ShadePresencePort | application (`application.port`) | app:data/ShadePresenceStateHolder.kt; app:di/FieldWatcherModule.kt; app:service/BubbleOverlayService.kt; app:service/FieldWatcherService.kt |
| StylePreferencePort | application (`application.port`) | app:data/DataStoreStylePreferenceStore.kt; app:di/VoiceModule.kt; app-test:testing/FakeStylePreferencePort.kt; core/application:application/evaluator/StyleAwareTextPostProcessor.kt; core/application:application/evaluator/StylingTextPostProcessor.kt; core/application:application/usecase/ObserveSelectedStyleUseCase.kt; core/application:application/usecase/SelectStyleUseCase.kt; core/application-test:testing/FakeStylePreferencePort.kt |
| TextInsertionPort | application (`application.port`) | app:di/BubbleModule.kt; app:service/InsertionTranscriptSink.kt; app:service/WatcherTextInsertion.kt; app-test:testing/FakeTextInsertionPort.kt |
| TranscriptSinkPort | application (`application.port`) | app:di/BubbleModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app:service/InsertionTranscriptSink.kt; app-test:testing/FakeTranscriptSinkPort.kt |
| TranscriptionEngine | application (`application.port`) | app:data/OnDeviceWhisperTranscriptionEngine.kt; app:data/ParakeetTranscriptionEngine.kt; app:data/SelectingTranscriptionEngine.kt; app:di/VoiceModule.kt; app:service/BubbleOverlayService.kt; app:service/BubbleRecordingController.kt; app-test:data/SelectingTranscriptionEngineTest.kt; app-test:testing/FakeTranscriptionEngine.kt |
| WatcherAccessPort | application (`application.port`) | app:data/AndroidWatcherAccess.kt; app:di/FieldWatcherModule.kt; app-test:testing/FakeWatcherAccessPort.kt; core/application:application/usecase/IsFieldWatcherEnabledUseCase.kt |
| WatcherConnectionPort | application (`application.port`) | app:data/WatcherConnectionStateHolder.kt; app:di/FieldWatcherModule.kt; app:service/FieldWatcherService.kt; app-test:testing/FakeWatcherConnectionPort.kt; core/application:application/usecase/ObserveWatcherConnectionUseCase.kt |
| TextPostProcessor | **domain** (`domain.port`) — ruling (c) | app:di/VoiceModule.kt; app-test:testing/FakeTextPostProcessor.kt; core/application:application/evaluator/DictionaryAwareTextPostProcessor.kt; core/application:application/evaluator/StyleAwareTextPostProcessor.kt; core/application:application/evaluator/StylingTextPostProcessor.kt; core/application-test:testing/FakeTextPostProcessor.kt; core/domain:domain/evaluator/BaselineTextPostProcessor.kt |

## Evaluator spot-audit (25 files)

| Evaluator | Port deps | Destination | Rationale |
|---|---|---|---|
| AmplitudeCalculator.kt | — | domain | pure transformation/policy, no application-port I/O |
| BaselineTextPostProcessor.kt | TextPostProcessor | domain | pure transformation/policy, no application-port I/O |
| BatteryExemptionIntentPolicy.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubbleAppearanceMath.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubbleDragSession.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubbleGestureClassifier.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubblePositionMath.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubbleServiceStartPolicy.kt | — | domain | pure transformation/policy, no application-port I/O |
| BubbleVisibilityReducer.kt | — | domain | pure transformation/policy, no application-port I/O |
| DictionaryCorrector.kt | — | domain | pure transformation/policy, no application-port I/O |
| ElapsedTimeFormatter.kt | — | domain | pure transformation/policy, no application-port I/O |
| EngineWarmingEvaluator.kt | — | domain | pure transformation/policy, no application-port I/O |
| FieldFocusEvaluator.kt | — | domain | pure transformation/policy, no application-port I/O |
| KeyboardWatchdogPolicy.kt | — | domain | pure transformation/policy, no application-port I/O |
| LlmResponseCleaner.kt | — | domain | pure transformation/policy, no application-port I/O |
| LowercaseCasualTransformer.kt | — | domain | pure transformation/policy, no application-port I/O |
| NotificationShadeFreezePolicy.kt | — | domain | pure transformation/policy, no application-port I/O |
| SilenceTrimmer.kt | — | domain | pure transformation/policy, no application-port I/O |
| StyleOutputGuard.kt | — | domain | pure transformation/policy, no application-port I/O |
| StylePromptBuilder.kt | — | domain | pure transformation/policy, no application-port I/O |
| TextSplicer.kt | — | domain | pure transformation/policy, no application-port I/O |
| WaveformHistory.kt | — | domain | pure transformation/policy, no application-port I/O |
| DictionaryAwareTextPostProcessor.kt | DictionaryPort, TextPostProcessor | **application** | rulings (a)/(b): chains/reads application ports |
| StyleAwareTextPostProcessor.kt | StylePreferencePort, TextPostProcessor | **application** | rulings (a)/(b): chains/reads application ports |
| StylingTextPostProcessor.kt | ApiKeyPort, LlmStyleOutcome, LlmStylePort, SendProgressPort, SendStage, StylePreferencePort, TextPostProcessor | **application** | rulings (a)/(b): chains/reads application ports |

## Module skeleton
```
:core:domain       pure JVM (java-library + kotlin-jvm, toolchain 17)
  domain.model / domain.evaluator / domain.port (TextPostProcessor only) / domain.diagnostics
  deps: coroutines-core, javax.inject
:core:application  pure JVM, api(:core:domain)
  application.usecase / application.port / application.evaluator (3 processors)
:app               depends on :core:application + :core:domain; data/service/presentation/di stay
```

## Hilt wiring consequences
`di/` (`BubbleModule`, `FieldWatcherModule`, `VoiceModule`) stays in `:app` as composition root: it now binds `application.port` interfaces to `data/`/`service/` implementations and `domain.port.TextPostProcessor` to `StyleAwareTextPostProcessor` (application). No Hilt module moves into the JVM modules (they stay Hilt-free except `javax.inject` constructor annotations). ViewModel use-case-only rule is now compiler-backed: `presentation/` cannot see ports except through `:app`'s transitive deps — actually `:app` is one module, so the compiler backing is at the core boundary (domain/application cannot see `:app`), and ViewModel->port remains agent/Detekt-enforced inside `:app` (stated honestly, not over-claimed).


## Per-test / per-fake placement
| Test/fake | Destination |
|---|---|

| core/domain-test:domain/diagnostics/CadenceCountersTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/AmplitudeCalculatorTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BaselineTextPostProcessorTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BatteryExemptionIntentPolicyTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubbleAppearanceMathTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubbleDragSessionTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubbleGestureClassifierTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubblePositionMathTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubbleServiceStartPolicyTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/BubbleVisibilityReducerTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/DictionaryCorrectorTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/ElapsedTimeFormatterTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/EngineWarmingEvaluatorTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/FieldFocusEvaluatorTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/KeyboardWatchdogPolicyTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/LlmResponseCleanerTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/LowercaseCasualTransformerTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/NotificationShadeFreezePolicyTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/SilenceTrimmerTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/StyleOutputGuardTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/StylePromptBuilderTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/TextSplicerTest.kt | :core:domain test |
| core/domain-test:domain/evaluator/WaveformHistoryTest.kt | :core:domain test |
| core/domain-test:domain/model/BubbleAppearanceTest.kt | :core:domain test |
| core/domain-test:domain/model/EffectiveEngineResolverTest.kt | :core:domain test |
| core/domain-test:domain/model/PersonalDictionaryTest.kt | :core:domain test |
| core/domain-test:domain/model/StyleCatalogTest.kt | :core:domain test |
| core/application-test:application/evaluator/DictionaryAwareTextPostProcessorTest.kt | :core:application test |
| core/application-test:application/evaluator/StyleAwareTextPostProcessorTest.kt | :core:application test |
| core/application-test:application/evaluator/StylingTextPostProcessorTest.kt | :core:application test |
| core/application-test:application/usecase/ObserveBubbleVisibilityUseCaseTest.kt | :core:application test |
| core/application-test:testing/FakeApiKeyPort.kt | :core:application test |
| core/application-test:testing/FakeDictionaryPort.kt | :core:application test |
| core/application-test:testing/FakeFieldFocusPort.kt | :core:application test |
| core/application-test:testing/FakeKeyboardVisibilityPort.kt | :core:application test |
| core/application-test:testing/FakeLlmStylePort.kt | :core:application test |
| core/application-test:testing/FakeSendProgressPort.kt | :core:application test |
| core/application-test:testing/FakeStylePreferencePort.kt | :core:application test |
| core/application-test:testing/FakeTextPostProcessor.kt | :core:application test |
Fakes copied (not moved — originals still needed by `:app` tests, which cannot see core test fixtures): FakeDictionaryPort, FakeStylePreferencePort, FakeApiKeyPort, FakeLlmStylePort, FakeSendProgressPort, FakeTextPostProcessor, FakeFieldFocusPort, FakeKeyboardVisibilityPort into `:core:application` test `testing/` package. All other fakes stay in `:app` test. No `:core:testing` module created.


## Fallback note
Package-split + Detekt import ban was **not** used. Per the STR-172 rule it is allowed only with a recorded concrete blocker; the real Gradle-module split compiled and tested green, so no blocker exists to record.
