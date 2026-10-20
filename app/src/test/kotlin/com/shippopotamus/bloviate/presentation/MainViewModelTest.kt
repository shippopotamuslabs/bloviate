package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.domain.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.domain.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.domain.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveEnginePreferenceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveFieldFocusUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveParakeetModelStateUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveResidentPresenceUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveWatcherConnectionUseCase
import com.shippopotamus.bloviate.domain.usecase.RecordBubbleUserStartUseCase
import com.shippopotamus.bloviate.domain.usecase.RecordBubbleUserStopUseCase
import com.shippopotamus.bloviate.domain.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.domain.usecase.SaveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.domain.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.domain.usecase.SetAutoStartEnabledUseCase
import com.shippopotamus.bloviate.testing.FakeBatteryExemptionPort
import com.shippopotamus.bloviate.testing.FakeBubbleAppearancePort
import com.shippopotamus.bloviate.testing.FakeBubbleOverlayAccessPort
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStatePort
import com.shippopotamus.bloviate.testing.FakeFieldFocusPort
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeMicPermissionPort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeResidentPresencePort
import com.shippopotamus.bloviate.testing.FakeWatcherAccessPort
import com.shippopotamus.bloviate.testing.FakeWatcherConnectionPort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainViewModelTest {

    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val fakeFieldFocusPort = FakeFieldFocusPort()
    private val fakeWatcherAccessPort = FakeWatcherAccessPort()
    private val fakeBubbleOverlayAccessPort = FakeBubbleOverlayAccessPort()
    private val fakeBubbleServiceStatePort = FakeBubbleServiceStatePort()
    private val fakeMicPermissionPort = FakeMicPermissionPort()
    private val fakeModelStorePort = FakeModelStorePort()
    private val fakeParakeetModelStorePort = FakeParakeetModelStorePort()
    private val fakeEnginePreferencePort = FakeEnginePreferencePort()
    private val fakeBubbleAppearancePort = FakeBubbleAppearancePort()
    private val fakeWatcherConnectionPort = FakeWatcherConnectionPort()
    private val fakeBatteryExemptionPort = FakeBatteryExemptionPort()
    private val fakeResidentPresencePort = FakeResidentPresencePort()
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setup() {
        viewModel = MainViewModel(
            observeFieldFocus = ObserveFieldFocusUseCase(fakeFieldFocusPort),
            isFieldWatcherEnabled = IsFieldWatcherEnabledUseCase(fakeWatcherAccessPort),
            isOverlayPermissionGranted = IsOverlayPermissionGrantedUseCase(fakeBubbleOverlayAccessPort),
            observeBubbleServiceRunning = ObserveBubbleServiceRunningUseCase(fakeBubbleServiceStatePort),
            isMicPermissionGranted = IsMicPermissionGrantedUseCase(fakeMicPermissionPort),
            refreshMicPermission = RefreshMicPermissionUseCase(fakeMicPermissionPort),
            observeModelState = ObserveModelStateUseCase(fakeModelStorePort),
            ensureModel = EnsureModelUseCase(fakeModelStorePort),
            observeParakeetModelState = ObserveParakeetModelStateUseCase(fakeParakeetModelStorePort),
            ensureParakeetModel = EnsureParakeetModelUseCase(fakeParakeetModelStorePort),
            observeEnginePreference = ObserveEnginePreferenceUseCase(fakeEnginePreferencePort),
            selectEngine = SelectEngineUseCase(fakeEnginePreferencePort),
            observeBubbleAppearance = ObserveBubbleAppearanceUseCase(fakeBubbleAppearancePort),
            saveBubbleAppearance = SaveBubbleAppearanceUseCase(fakeBubbleAppearancePort),
            observeWatcherConnection = ObserveWatcherConnectionUseCase(fakeWatcherConnectionPort),
            isBatteryOptimizationExempt = IsBatteryOptimizationExemptUseCase(fakeBatteryExemptionPort),
            observeResidentPresence = ObserveResidentPresenceUseCase(fakeResidentPresencePort),
            setAutoStartEnabled = SetAutoStartEnabledUseCase(fakeResidentPresencePort),
            recordBubbleUserStart = RecordBubbleUserStartUseCase(fakeResidentPresencePort),
            recordBubbleUserStop = RecordBubbleUserStopUseCase(fakeResidentPresencePort),
        )
    }

    @Test
    fun `initial state has no focus and reflects disabled watcher`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertFalse(initial.watcherEnabled)
                assertEquals(FocusClassification.NONE, initial.focusClassification)
                assertNull(initial.focusPackageName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editable focus maps to editable classification with package name`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_NAME))

                val updated = awaitItem()
                assertEquals(FocusClassification.EDITABLE, updated.focusClassification)
                assertEquals(PACKAGE_NAME, updated.focusPackageName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `password focus maps to password classification with package name`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeFieldFocusPort.publish(FieldFocusState.PasswordFocus(PACKAGE_NAME))

                val updated = awaitItem()
                assertEquals(FocusClassification.PASSWORD, updated.focusClassification)
                assertEquals(PACKAGE_NAME, updated.focusPackageName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onResumed re-reads the watcher enabled setting`() =
        runTest(mainDispatcherExtension.testDispatcher) {
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
            viewModel.uiState.test {
                assertFalse(awaitItem().micPermissionGranted)

                fakeMicPermissionPort.liveGranted = true
                viewModel.onResumed()

                assertTrue(awaitItem().micPermissionGranted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `mic permission result re-reads the grant and republishes it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
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
    fun `bubble service running state flows into ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertFalse(awaitItem().bubbleServiceRunning)

                fakeBubbleServiceStatePort.publishServiceRunning(true)

                assertTrue(awaitItem().bubbleServiceRunning)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `model starts absent in ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertEquals(ModelUiStatus.Absent, awaitItem().whisperModelStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `model downloading maps to a percent in ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeModelStorePort.setState(ModelState.Downloading(progressFraction = 0.42f))

                assertEquals(ModelUiStatus.Downloading(percent = 42), awaitItem().whisperModelStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `model ready maps to ready ui status`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeModelStorePort.setReady()

                assertEquals(ModelUiStatus.Ready, awaitItem().whisperModelStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `model failure maps its reason into ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeModelStorePort.setState(ModelState.Failed("IOException: network down"))

                assertEquals(
                    ModelUiStatus.Failed("IOException: network down"),
                    awaitItem().whisperModelStatus,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `whisper download click runs ensure on the whisper store`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            assertEquals(0, fakeModelStorePort.ensureModelCallCount)

            viewModel.onWhisperDownloadClick()
            advanceUntilIdle()

            assertEquals(1, fakeModelStorePort.ensureModelCallCount)
        }

    @Test
    fun `parakeet model state maps into its own ui status`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeParakeetModelStorePort.setState(ModelState.Downloading(progressFraction = 0.5f))

                val updated = awaitItem()
                assertEquals(ModelUiStatus.Downloading(percent = 50), updated.parakeetModelStatus)
                assertEquals(ModelUiStatus.Absent, updated.whisperModelStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parakeet download click runs ensure on the parakeet store`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            assertEquals(0, fakeParakeetModelStorePort.ensureModelCallCount)

            viewModel.onParakeetDownloadClick()
            advanceUntilIdle()

            assertEquals(1, fakeParakeetModelStorePort.ensureModelCallCount)
        }

    @Test
    fun `engine selection defaults to parakeet and follows the preference`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertEquals(DictationEngineUi.PARAKEET, awaitItem().selectedEngine)

                fakeEnginePreferencePort.setSelected(DictationEngine.WHISPER)

                assertEquals(DictationEngineUi.WHISPER, awaitItem().selectedEngine)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fallback note shows while the selected parakeet model is missing and whisper is ready`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertNull(awaitItem().engineFallback)

                fakeModelStorePort.setReady()

                assertEquals(
                    EngineFallbackUi(
                        selected = DictationEngineUi.PARAKEET,
                        effective = DictationEngineUi.WHISPER,
                    ),
                    awaitItem().engineFallback,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fallback note clears when the selected engine's model becomes ready`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeModelStorePort.setReady()
                assertEquals(
                    EngineFallbackUi(
                        selected = DictationEngineUi.PARAKEET,
                        effective = DictationEngineUi.WHISPER,
                    ),
                    awaitItem().engineFallback,
                )

                fakeParakeetModelStorePort.setReady()

                assertNull(awaitItem().engineFallback)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fallback note shows whisper selected with parakeet effective`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeEnginePreferencePort.setSelected(DictationEngine.WHISPER)
                // Neither model is ready yet: no fallback to report.
                assertNull(awaitItem().engineFallback)

                fakeParakeetModelStorePort.setReady()

                assertEquals(
                    EngineFallbackUi(
                        selected = DictationEngineUi.WHISPER,
                        effective = DictationEngineUi.PARAKEET,
                    ),
                    awaitItem().engineFallback,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting an engine persists it through the use case`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.onSelectEngine(DictationEngineUi.PARAKEET)
            advanceUntilIdle()

            assertEquals(DictationEngine.PARAKEET, fakeEnginePreferencePort.selectedEngine.value)
            assertEquals(1, fakeEnginePreferencePort.selectCallCount)
        }

    @Test
    fun `bubble appearance starts at the default look in ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertEquals(BubbleAppearanceUi(), awaitItem().bubbleAppearance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a published appearance maps to ui enums in ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeBubbleAppearancePort.publish(
                    BubbleAppearance(
                        opacity = 0.6f,
                        size = BubbleSize.SMALL,
                        shape = BubbleShape.ARCH,
                        tint = BubbleTint.WARNING,
                    ),
                )

                assertEquals(
                    BubbleAppearanceUi(
                        opacity = 0.6f,
                        size = BubbleSizeUi.SMALL,
                        shape = BubbleShapeUi.ARCH,
                        tint = BubbleTintUi.AMBER,
                    ),
                    awaitItem().bubbleAppearance,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `opacity change persists only the opacity of the current appearance`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeBubbleAppearancePort.publish(
                BubbleAppearance(size = BubbleSize.LARGE, shape = BubbleShape.COOKIE, tint = BubbleTint.INFO),
            )

            viewModel.onBubbleOpacityChange(0.5f)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    BubbleAppearance(
                        opacity = 0.5f,
                        size = BubbleSize.LARGE,
                        shape = BubbleShape.COOKIE,
                        tint = BubbleTint.INFO,
                    ),
                ),
                fakeBubbleAppearancePort.savedAppearances,
            )
        }

    @Test
    fun `opacity change below the floor is coerced before it persists`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.onBubbleOpacityChange(0.1f)
            advanceUntilIdle()

            assertEquals(
                BubbleAppearance.MIN_OPACITY,
                fakeBubbleAppearancePort.savedAppearances.single().opacity,
            )
        }

    @Test
    fun `size shape and tint changes persist mapped to their domain values`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.onBubbleSizeChange(BubbleSizeUi.LARGE)
            advanceUntilIdle()
            viewModel.onBubbleShapeChange(BubbleShapeUi.CLOVER)
            advanceUntilIdle()
            viewModel.onBubbleTintChange(BubbleTintUi.SKY)
            advanceUntilIdle()

            assertEquals(3, fakeBubbleAppearancePort.saveCallCount)
            // The fake republishes each save, so the edits accumulate on
            // the live preference — the last save carries all three.
            assertEquals(
                BubbleAppearance(
                    size = BubbleSize.LARGE,
                    shape = BubbleShape.CLOVER,
                    tint = BubbleTint.INFO,
                ),
                fakeBubbleAppearancePort.savedAppearances.last(),
            )
        }

    // ENC-129: watcher supervision, battery exemption, resident presence.

    @Test
    fun `watcher connection state flows into the UI state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertFalse(awaitItem().watcherConnected)
                fakeWatcherConnectionPort.publishConnected(true)
                assertTrue(awaitItem().watcherConnected)
                fakeWatcherConnectionPort.publishConnected(false)
                assertFalse(awaitItem().watcherConnected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `battery exemption is read at start and refreshed on resume`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertFalse(awaitItem().batteryExempt)
                fakeBatteryExemptionPort.exempt = true
                viewModel.onResumed()
                assertTrue(awaitItem().batteryExempt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `auto-start switch state flows from the resident-presence record`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertTrue(awaitItem().autoStartEnabled)
                fakeResidentPresencePort.publish(ResidentPresenceState(autoStartEnabled = false))
                assertFalse(awaitItem().autoStartEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `auto-start toggle persists without clobbering the stop flag`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeResidentPresencePort.publish(
                ResidentPresenceState(autoStartEnabled = true, userStopped = true),
            )

            viewModel.onAutoStartEnabledChange(false)
            advanceUntilIdle()

            assertEquals(
                ResidentPresenceState(autoStartEnabled = false, userStopped = true),
                fakeResidentPresencePort.state.value,
            )
        }

    @Test
    fun `bubble stop request records the stop, start request lifts it`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.onBubbleStopRequested()
            advanceUntilIdle()
            assertTrue(fakeResidentPresencePort.state.value.userStopped)

            viewModel.onBubbleStartRequested()
            advanceUntilIdle()
            assertFalse(fakeResidentPresencePort.state.value.userStopped)
        }

    private companion object {
        const val PACKAGE_NAME = "com.example.host"
    }
}
