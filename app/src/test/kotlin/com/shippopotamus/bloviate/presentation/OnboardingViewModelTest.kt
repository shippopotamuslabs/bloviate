package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.OnboardingStep
import com.shippopotamus.bloviate.domain.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.domain.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.domain.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveEnginePreferenceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveOnboardingProgressUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveParakeetModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveWatcherConnectionUseCase
import com.shippopotamus.bloviate.domain.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveOnboardingProgressUseCase
import com.shippopotamus.bloviate.domain.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.testing.FakeBatteryExemptionPort
import com.shippopotamus.bloviate.testing.FakeBubbleOverlayAccessPort
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStatePort
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeMicPermissionPort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeOnboardingPort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeWatcherAccessPort
import com.shippopotamus.bloviate.testing.FakeWatcherConnectionPort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class OnboardingViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeOnboardingPort = FakeOnboardingPort()
    private val fakeWatcherAccessPort = FakeWatcherAccessPort()
    private val fakeBubbleOverlayAccessPort = FakeBubbleOverlayAccessPort()
    private val fakeMicPermissionPort = FakeMicPermissionPort()
    private val fakeBubbleServiceStatePort = FakeBubbleServiceStatePort()
    private val fakeModelStorePort = FakeModelStorePort()
    private val fakeParakeetModelStorePort = FakeParakeetModelStorePort()
    private val fakeEnginePreferencePort = FakeEnginePreferencePort()
    private val fakeWatcherConnectionPort = FakeWatcherConnectionPort()
    private val fakeBatteryExemptionPort = FakeBatteryExemptionPort()

    /** Builds the ViewModel over the current fake state; configure fakes first. */
    private fun buildViewModel(): OnboardingViewModel = OnboardingViewModel(
        observeOnboardingProgress = ObserveOnboardingProgressUseCase(fakeOnboardingPort),
        saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeOnboardingPort),
        isFieldWatcherEnabled = IsFieldWatcherEnabledUseCase(fakeWatcherAccessPort),
        isOverlayPermissionGranted = IsOverlayPermissionGrantedUseCase(fakeBubbleOverlayAccessPort),
        isMicPermissionGranted = IsMicPermissionGrantedUseCase(fakeMicPermissionPort),
        refreshMicPermission = RefreshMicPermissionUseCase(fakeMicPermissionPort),
        observeBubbleServiceRunning = ObserveBubbleServiceRunningUseCase(fakeBubbleServiceStatePort),
        observeModelState = ObserveModelStateUseCase(fakeModelStorePort),
        ensureModel = EnsureModelUseCase(fakeModelStorePort),
        observeParakeetModelState = ObserveParakeetModelStateUseCase(fakeParakeetModelStorePort),
        ensureParakeetModel = EnsureParakeetModelUseCase(fakeParakeetModelStorePort),
        observeEnginePreference = ObserveEnginePreferenceUseCase(fakeEnginePreferencePort),
        selectEngine = SelectEngineUseCase(fakeEnginePreferencePort),
        observeWatcherConnection = ObserveWatcherConnectionUseCase(fakeWatcherConnectionPort),
        isBatteryOptimizationExempt = IsBatteryOptimizationExemptUseCase(fakeBatteryExemptionPort),
    )

    @Test
    fun `an unfinished walk shows the wizard at the persisted step`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.MODEL, finished = false),
            )
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.showWizard)
                assertEquals(OnboardingStepUi.MODEL, state.step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a finished walk keeps the wizard closed`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.TEST, finished = true),
            )
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().showWizard)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `continue advances one step and persists it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(
                listOf(OnboardingProgress(step = OnboardingStep.WATCHER, finished = false)),
                fakeOnboardingPort.savedProgresses,
            )

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(
                OnboardingProgress(step = OnboardingStep.OVERLAY, finished = false),
                fakeOnboardingPort.progress.value,
            )
        }

    @Test
    fun `continue on the test step finishes the walk`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.TEST, finished = false),
            )
            val viewModel = buildViewModel()

            viewModel.onContinue()
            advanceUntilIdle()

            assertEquals(
                OnboardingProgress(step = OnboardingStep.TEST, finished = true),
                fakeOnboardingPort.progress.value,
            )
        }

    @Test
    fun `back returns one step and persists it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.OVERLAY, finished = false),
            )
            val viewModel = buildViewModel()

            viewModel.onBack()
            advanceUntilIdle()

            assertEquals(
                OnboardingProgress(step = OnboardingStep.WATCHER, finished = false),
                fakeOnboardingPort.progress.value,
            )
        }

    @Test
    fun `back on the welcome step saves nothing`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onBack()
            advanceUntilIdle()

            assertEquals(0, fakeOnboardingPort.saveCallCount)
        }

    @Test
    fun `skip finishes in place and keeps the resume step`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.MICROPHONE, finished = false),
            )
            val viewModel = buildViewModel()

            viewModel.onSkip()
            advanceUntilIdle()

            assertEquals(
                OnboardingProgress(step = OnboardingStep.MICROPHONE, finished = true),
                fakeOnboardingPort.progress.value,
            )
        }

    @Test
    fun `run setup again restarts the walk unfinished at welcome`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.TEST, finished = true),
            )
            val viewModel = buildViewModel()

            viewModel.onRunSetupAgain()
            advanceUntilIdle()

            assertEquals(OnboardingProgress.NOT_STARTED, fakeOnboardingPort.progress.value)
        }

    @Test
    fun `onResumed re-reads the watcher enabled setting`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().watcherEnabled)

                fakeWatcherAccessPort.enabled = true
                viewModel.onResumed()

                assertTrue(awaitItem().watcherEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `overlay permission state is reflected and re-read on resume`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().overlayPermissionGranted)

                fakeBubbleOverlayAccessPort.canDraw = true
                viewModel.onResumed()

                assertTrue(awaitItem().overlayPermissionGranted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `mic permission state is reflected and re-read on resume`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().micPermissionGranted)

                fakeMicPermissionPort.liveGranted = true
                viewModel.onResumed()

                assertTrue(awaitItem().micPermissionGranted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `mic permission result re-reads the live grant`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().micPermissionGranted)

                fakeMicPermissionPort.liveGranted = true
                viewModel.onMicPermissionResult()

                assertTrue(awaitItem().micPermissionGranted)
                assertTrue(fakeMicPermissionPort.refreshCount > 0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `model download progress maps through and ready satisfies the model step`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()

                fakeParakeetModelStorePort.setState(ModelState.Downloading(progressFraction = 0.25f))

                val downloading = awaitItem()
                assertEquals(
                    ModelUiStatus.Downloading(percent = 25),
                    downloading.parakeetModelStatus,
                )
                assertFalse(downloading.stepSatisfied(OnboardingStepUi.MODEL))

                fakeParakeetModelStorePort.setReady()

                val ready = awaitItem()
                assertEquals(ModelUiStatus.Ready, ready.parakeetModelStatus)
                assertTrue(ready.stepSatisfied(OnboardingStepUi.MODEL))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a ready whisper model also satisfies the model step`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()

                fakeModelStorePort.setReady()

                assertTrue(awaitItem().stepSatisfied(OnboardingStepUi.MODEL))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `download clicks route to the matching engine store`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onWhisperDownloadClick()
            viewModel.onParakeetDownloadClick()
            advanceUntilIdle()

            assertEquals(1, fakeModelStorePort.ensureModelCallCount)
            assertEquals(1, fakeParakeetModelStorePort.ensureModelCallCount)
        }

    @Test
    fun `engine selection persists through the preference`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.onSelectEngine(DictationEngineUi.WHISPER)
            advanceUntilIdle()

            assertEquals(DictationEngine.WHISPER, fakeEnginePreferencePort.selectedEngine.value)
        }

    @Test
    fun `the fallback note follows the shared resolver while the selected model is absent`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeModelStorePort.setReady()
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(DictationEngineUi.PARAKEET, state.selectedEngine)
                assertEquals(
                    EngineFallbackUi(
                        selected = DictationEngineUi.PARAKEET,
                        effective = DictationEngineUi.WHISPER,
                    ),
                    state.engineFallback,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no fallback note once the selected engine is the ready one`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeParakeetModelStorePort.setReady()
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertNull(awaitItem().engineFallback)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `test field text lands in state and drives the landed signal`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().testDictationLanded())

                viewModel.onTestFieldTextChange("dictated words")

                val updated = awaitItem()
                assertEquals("dictated words", updated.testFieldText)
                assertTrue(updated.testDictationLanded())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the test step is satisfied only when the whole chain is live`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeWatcherAccessPort.enabled = true
            fakeWatcherConnectionPort.publishConnected(true)
            fakeBubbleOverlayAccessPort.canDraw = true
            fakeMicPermissionPort.setGranted(true)
            fakeParakeetModelStorePort.setReady()
            fakeBubbleServiceStatePort.publishServiceRunning(true)
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertTrue(awaitItem().stepSatisfied(OnboardingStepUi.TEST))

                fakeBubbleServiceStatePort.publishServiceRunning(false)

                assertFalse(awaitItem().stepSatisfied(OnboardingStepUi.TEST))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ENC-129: the battery step, supervised watcher step, walk order.

    @Test
    fun `continue walks microphone to battery to model`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeOnboardingPort.publish(
                OnboardingProgress(step = OnboardingStep.MICROPHONE, finished = false),
            )
            val viewModel = buildViewModel()

            viewModel.onContinue()
            advanceUntilIdle()
            assertEquals(
                OnboardingProgress(step = OnboardingStep.BATTERY, finished = false),
                fakeOnboardingPort.progress.value,
            )

            viewModel.onContinue()
            advanceUntilIdle()
            assertEquals(
                OnboardingProgress(step = OnboardingStep.MODEL, finished = false),
                fakeOnboardingPort.progress.value,
            )
        }

    @Test
    fun `battery exemption is read at start and refreshed on resume`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertFalse(awaitItem().stepSatisfied(OnboardingStepUi.BATTERY))

                fakeBatteryExemptionPort.exempt = true
                viewModel.onResumed()

                assertTrue(awaitItem().stepSatisfied(OnboardingStepUi.BATTERY))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `watcher step follows the connection stream`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeWatcherAccessPort.enabled = true
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                // Enabled but not yet connected: supervised unsatisfied.
                assertFalse(awaitItem().stepSatisfied(OnboardingStepUi.WATCHER))

                fakeWatcherConnectionPort.publishConnected(true)

                assertTrue(awaitItem().stepSatisfied(OnboardingStepUi.WATCHER))
                cancelAndIgnoreRemainingEvents()
            }
        }
}
