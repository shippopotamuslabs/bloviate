package com.shippopotamus.bloviate.presentation

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.application.evaluator.LiveSetupStateResolver
import com.shippopotamus.bloviate.application.usecase.EnsureModelUseCase
import com.shippopotamus.bloviate.application.usecase.EnsureParakeetModelUseCase
import com.shippopotamus.bloviate.application.usecase.IsBatteryOptimizationExemptUseCase
import com.shippopotamus.bloviate.application.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.application.usecase.IsMicPermissionGrantedUseCase
import com.shippopotamus.bloviate.application.usecase.IsOverlayPermissionGrantedUseCase
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import com.shippopotamus.bloviate.application.usecase.ObserveLiveSetupStateUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStartUseCase
import com.shippopotamus.bloviate.application.usecase.RecordBubbleUserStopUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshCustomStylesUseCase
import com.shippopotamus.bloviate.application.usecase.RefreshMicPermissionUseCase
import com.shippopotamus.bloviate.application.usecase.SaveBubbleAppearanceUseCase
import com.shippopotamus.bloviate.application.usecase.SelectEngineUseCase
import com.shippopotamus.bloviate.application.usecase.SetAutoStartEnabledUseCase
import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportReason
import com.shippopotamus.bloviate.application.usecase.ClearLastStyledResultUseCase
import com.shippopotamus.bloviate.application.usecase.DeleteAccountUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveLastStyledResultUseCase
import com.shippopotamus.bloviate.application.usecase.ObserveSelectedStyleUseCase
import com.shippopotamus.bloviate.application.usecase.SelectStyleUseCase
import com.shippopotamus.bloviate.application.usecase.SignInWithGoogleUseCase
import com.shippopotamus.bloviate.application.usecase.SignOutUseCase
import com.shippopotamus.bloviate.application.usecase.SubmitOutputReportUseCase
import com.shippopotamus.bloviate.testing.FakeAccountDeletionPort
import com.shippopotamus.bloviate.testing.FakeAuthSession
import com.shippopotamus.bloviate.testing.FakeLastStyledResultPort
import com.shippopotamus.bloviate.testing.FakeOutputReportPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.FakeBatteryExemptionPort
import com.shippopotamus.bloviate.testing.FakeCustomStyleStorePort
import com.shippopotamus.bloviate.testing.FakeCustomStyleSyncPort
import com.shippopotamus.bloviate.testing.FakeBubbleAppearancePort
import com.shippopotamus.bloviate.testing.FakeBubbleOverlayAccessPort
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStatePort
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeMicPermissionPort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeOnboardingPort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeResidentPresencePort
import com.shippopotamus.bloviate.testing.FakeWatcherAccessPort
import com.shippopotamus.bloviate.testing.FakeWatcherConnectionPort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
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
    private val fakeAuthSession = FakeAuthSession()
    private val fakeOnboardingPort = FakeOnboardingPort()

    private val resolverScope = TestScope(mainDispatcherExtension.testDispatcher)
    private val fakeCustoms = FakeCustomStyleStorePort()
    private val fakeSync = FakeCustomStyleSyncPort()
    private val fakeDeletion = FakeAccountDeletionPort()
    private val fakeReport = FakeOutputReportPort()
    private val fakeLastResult = FakeLastStyledResultPort()
    private val fakeStylePreference = FakeStylePreferencePort()
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setup() {
        viewModel = buildViewModel(auth = fakeAuthSession)
    }

    private fun buildViewModel(auth: FakeAuthSession): MainViewModel =
        MainViewModel(
            observeLiveSetupState = ObserveLiveSetupStateUseCase(
                LiveSetupStateResolver(
                    watcherConnection = fakeWatcherConnectionPort,
                    bubbleServiceState = fakeBubbleServiceStatePort,
                    whisperStore = fakeModelStorePort,
                    parakeetStore = fakeParakeetModelStorePort,
                    enginePreference = fakeEnginePreferencePort,
                    bubbleAppearance = fakeBubbleAppearancePort,
                    residentPresence = fakeResidentPresencePort,
                    authSession = auth,
                    onboarding = fakeOnboardingPort,
                    scope = resolverScope,
                ),
            ),
            isFieldWatcherEnabled = IsFieldWatcherEnabledUseCase(fakeWatcherAccessPort),
            isOverlayPermissionGranted = IsOverlayPermissionGrantedUseCase(fakeBubbleOverlayAccessPort),
            isMicPermissionGranted = IsMicPermissionGrantedUseCase(fakeMicPermissionPort),
            refreshMicPermission = RefreshMicPermissionUseCase(fakeMicPermissionPort),
            ensureModel = EnsureModelUseCase(fakeModelStorePort),
            ensureParakeetModel = EnsureParakeetModelUseCase(fakeParakeetModelStorePort),
            selectEngine = SelectEngineUseCase(fakeEnginePreferencePort),
            saveBubbleAppearance = SaveBubbleAppearanceUseCase(fakeBubbleAppearancePort),
            isBatteryOptimizationExempt = IsBatteryOptimizationExemptUseCase(fakeBatteryExemptionPort),
            setAutoStartEnabled = SetAutoStartEnabledUseCase(fakeResidentPresencePort),
            recordBubbleUserStart = RecordBubbleUserStartUseCase(fakeResidentPresencePort),
            recordBubbleUserStop = RecordBubbleUserStopUseCase(fakeResidentPresencePort),
            signInWithGoogle = SignInWithGoogleUseCase(auth),
            signOut = SignOutUseCase(auth),
            refreshCustomStyles = RefreshCustomStylesUseCase(sync = fakeSync, customs = fakeCustoms),
            deleteAccount = DeleteAccountUseCase(
                deletionPort = fakeDeletion,
                authSession = auth,
                customStyleStore = fakeCustoms,
                stylePreference = fakeStylePreference,
                lastStyledResult = fakeLastResult,
            ),
            submitOutputReport = SubmitOutputReportUseCase(
                reportPort = fakeReport,
                lastResultPort = fakeLastResult,
            ),
            observeLastStyledResult = ObserveLastStyledResultUseCase(fakeLastResult),
            observeSelectedStyle = ObserveSelectedStyleUseCase(fakeStylePreference),
            selectStyle = SelectStyleUseCase(fakeStylePreference),
            clearLastStyledResult = ClearLastStyledResultUseCase(fakeLastResult),
        )

    @Test
    fun `initial state reflects disabled watcher`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertFalse(initial.watcherEnabled)
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
            // BLO-175: the current appearance reaches the save path through
            // the combined live state now, which settles asynchronously —
            // let it land before the click reads it.
            advanceUntilIdle()

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

    // ENC-197: the launcher auth gate.

    @Test
    fun `gate starts signed out while the session is unauthenticated`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertTrue(initial.auth is AuthUi.SignedOut)
                assertFalse(initial.isSigningIn)
                assertNull(initial.signInError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `published google session maps to a signed-in gate with profile strings`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                awaitItem()

                fakeAuthSession.publish(
                    AuthState.Authenticated(
                        UserProfile(
                            uid = "uid-1",
                            displayName = "Sean",
                            email = "sean@example.com",
                            photoUrl = null,
                        ),
                    ),
                )

                val signedIn = awaitItem().auth
                assertTrue(signedIn is AuthUi.SignedIn)
                assertEquals("Sean", (signedIn as AuthUi.SignedIn).displayName)
                assertEquals("sean@example.com", signedIn.email)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no custom-styles refresh while signed out`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            advanceUntilIdle()

            assertEquals(0, fakeSync.listCallCount)
        }

    @Test
    fun `a fresh sign-in refreshes the custom-styles cache`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            advanceUntilIdle()
            assertEquals(0, fakeSync.listCallCount)

            fakeAuthSession.publish(
                AuthState.Authenticated(
                    UserProfile(uid = "uid-1", displayName = null, email = null, photoUrl = null),
                ),
            )
            advanceUntilIdle()

            assertEquals(1, fakeSync.listCallCount)
        }

    @Test
    fun `an already-signed-in start refreshes the custom-styles cache`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            val signedInAuth = FakeAuthSession(
                initialState = AuthState.Authenticated(
                    UserProfile(uid = "uid-1", displayName = null, email = null, photoUrl = null),
                ),
            )
            buildViewModel(auth = signedInAuth)
            advanceUntilIdle()

            assertEquals(1, fakeSync.listCallCount)
        }

    @Test
    fun `sign-in success spins then clears without an error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeAuthSession.stubSignIn(
                Result.success(UserProfile(uid = "uid-1", displayName = null, email = null, photoUrl = null)),
            )
            val gate = CompletableDeferred<Unit>()
            fakeAuthSession.signInGate = gate

            viewModel.uiState.test {
                awaitItem()

                viewModel.onSignInClick()

                assertTrue(awaitItem().isSigningIn)
                gate.complete(Unit)
                val done = awaitItem()
                assertFalse(done.isSigningIn)
                assertNull(done.signInError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sign-in failure maps to its user-facing message`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeAuthSession.stubSignIn(Result.failure(IOException("timeout")))
            val gate = CompletableDeferred<Unit>()
            fakeAuthSession.signInGate = gate

            viewModel.uiState.test {
                awaitItem()

                viewModel.onSignInClick()

                assertTrue(awaitItem().isSigningIn)
                gate.complete(Unit)
                val done = awaitItem()
                assertFalse(done.isSigningIn)
                assertEquals(MSG_NETWORK, done.signInError)

                viewModel.onSignInErrorDismiss()
                assertNull(awaitItem().signInError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sign-in cancellation stays silent`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeAuthSession.stubSignIn(
                Result.failure(GetCredentialCancellationException()),
            )
            val gate = CompletableDeferred<Unit>()
            fakeAuthSession.signInGate = gate

            viewModel.uiState.test {
                awaitItem()

                viewModel.onSignInClick()

                assertTrue(awaitItem().isSigningIn)
                gate.complete(Unit)
                val done = awaitItem()
                assertFalse(done.isSigningIn)
                assertNull(done.signInError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sign-out click runs the sign-out use case`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            assertEquals(0, fakeAuthSession.signOutCallCount)

            viewModel.onSignOutClick()
            advanceUntilIdle()

            assertEquals(1, fakeAuthSession.signOutCallCount)
        }

    // BLO-178: sign-out cache hygiene.

    @Test
    fun `sign-out resets a custom selection and drops the last styled result`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeStylePreference.setSelected("custom-mine")
            fakeLastResult.record(sampleLastResult())

            viewModel.onSignOutClick()
            advanceUntilIdle()

            assertEquals(1, fakeAuthSession.signOutCallCount)
            assertEquals(
                com.shippopotamus.bloviate.domain.model.StyleCatalog.DEFAULT.id,
                fakeStylePreference.selectedStyleId.value,
            )
            assertNull(fakeLastResult.lastResult.value)
        }

    @Test
    fun `sign-out keeps a built-in selection`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeStylePreference.setSelected("professional")

            viewModel.onSignOutClick()
            advanceUntilIdle()

            assertEquals("professional", fakeStylePreference.selectedStyleId.value)
            assertEquals(0, fakeStylePreference.selectCallCount)
        }

    // BLO-178: account deletion flow.

    private fun stubSignInSuccess() {
        fakeAuthSession.stubSignIn(
            Result.success(UserProfile(uid = "uid-1", displayName = null, email = null, photoUrl = null)),
        )
    }

    /**
     * Drains uiState emissions until one matches. The stateIn initial
     * value (computed before the fakes are seeded) and intermediate
     * combine emissions make fixed awaitItem counts racy, so the
     * BLO-178 / BLO-148 tests await by predicate, in action order.
     */
    private suspend fun ReceiveTurbine<MainUiState>.awaitState(
        predicate: (MainUiState) -> Boolean,
    ): MainUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    @Test
    fun `deletion re-authenticates first, then deletes and cleans up`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            stubSignInSuccess()
            val gate = CompletableDeferred<Unit>()
            fakeAuthSession.signInGate = gate
            fakeDeletion.outcome = AccountDeletionOutcome.Deleted

            viewModel.uiState.test {
                viewModel.onDeleteAccountClick()
                awaitState { it.deletion.step == DeletionStepUi.CONFIRM }

                viewModel.onDeleteAccountContinue()
                awaitState { it.deletion.step == DeletionStepUi.FINAL }

                viewModel.onDeleteAccountConfirm()
                awaitState { it.deletion.isDeleting }
                // The re-auth gate is still closed: the server delete
                // must not have run yet — re-auth comes first.
                assertEquals(0, fakeDeletion.callCount)

                gate.complete(Unit)
                val done = awaitState { !it.deletion.isDeleting }
                assertEquals(DeletionStepUi.NONE, done.deletion.step)
                assertTrue(done.deletion.accountDeleted)
                assertEquals(1, fakeDeletion.callCount)
                assertEquals(1, fakeCustoms.clearLocalCacheCallCount)
                assertEquals(1, fakeAuthSession.signOutCallCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a re-auth failure stops the deletion with an error on the final step`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeAuthSession.stubSignIn(Result.failure(IOException("timeout")))

            viewModel.uiState.test {
                viewModel.onDeleteAccountClick()
                awaitState { it.deletion.step == DeletionStepUi.CONFIRM }
                viewModel.onDeleteAccountContinue()
                awaitState { it.deletion.step == DeletionStepUi.FINAL }

                viewModel.onDeleteAccountConfirm()
                val state = awaitState { it.deletion.error == MSG_DELETE_SIGN_IN_FAILED }

                assertEquals(DeletionStepUi.FINAL, state.deletion.step)
                assertEquals(0, fakeDeletion.callCount)
                assertEquals(0, fakeCustoms.clearLocalCacheCallCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a re-auth dismissal closes the deletion silently`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeAuthSession.stubSignIn(Result.failure(GetCredentialCancellationException()))

            viewModel.uiState.test {
                viewModel.onDeleteAccountClick()
                awaitState { it.deletion.step == DeletionStepUi.CONFIRM }
                viewModel.onDeleteAccountContinue()
                awaitState { it.deletion.step == DeletionStepUi.FINAL }

                viewModel.onDeleteAccountConfirm()
                val state = awaitState {
                    it.deletion.step == DeletionStepUi.NONE && !it.deletion.isDeleting
                }

                assertNull(state.deletion.error)
                assertEquals(0, fakeDeletion.callCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a requires-recent-login outcome keeps the final step with its error`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            stubSignInSuccess()
            fakeDeletion.outcome = AccountDeletionOutcome.RequiresRecentLogin

            viewModel.uiState.test {
                viewModel.onDeleteAccountClick()
                awaitState { it.deletion.step == DeletionStepUi.CONFIRM }
                viewModel.onDeleteAccountContinue()
                awaitState { it.deletion.step == DeletionStepUi.FINAL }

                viewModel.onDeleteAccountConfirm()
                val state = awaitState { it.deletion.error == MSG_DELETE_RECENT_LOGIN }

                assertEquals(DeletionStepUi.FINAL, state.deletion.step)
                assertEquals(0, fakeCustoms.clearLocalCacheCallCount)
                assertEquals(0, fakeAuthSession.signOutCallCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unavailable outcome keeps the final step open for retry`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            stubSignInSuccess()
            fakeDeletion.outcome = AccountDeletionOutcome.Unavailable

            viewModel.uiState.test {
                viewModel.onDeleteAccountClick()
                awaitState { it.deletion.step == DeletionStepUi.CONFIRM }
                viewModel.onDeleteAccountContinue()
                awaitState { it.deletion.step == DeletionStepUi.FINAL }

                viewModel.onDeleteAccountConfirm()
                val state = awaitState { it.deletion.error == MSG_DELETE_UNAVAILABLE }

                assertEquals(DeletionStepUi.FINAL, state.deletion.step)
                assertFalse(state.deletion.accountDeleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // BLO-148: output reporting.

    private fun sampleLastResult(): LastStyledResult = LastStyledResult(
        styleId = "seneca",
        sourceTranscript = "the dictated words",
        styledOutput = "The styled words.",
        model = "model-x",
        provider = "provider-y",
        recordedAtEpochMillis = 4242L,
    )

    @Test
    fun `the last styled result surfaces as a last-dictation card state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeLastResult.record(sampleLastResult())

            viewModel.uiState.test {
                val state = awaitState { it.lastDictation != null }
                val card = state.lastDictation
                assertEquals("seneca", card?.styleId)
                assertEquals("The styled words.", card?.styledOutput)
                assertEquals(false, card?.isReported)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `opening the picker sends nothing and a reason tap submits once`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeLastResult.record(sampleLastResult())

            viewModel.uiState.test {
                viewModel.onReportClick()
                awaitState { it.report.pickerOpen }
                assertTrue(fakeReport.requests.isEmpty())

                viewModel.onReportReasonClick(ReportReasonUi.WRONG)
                val state = awaitState { it.report.reportSent }

                assertFalse(state.report.pickerOpen)
                assertEquals(true, state.lastDictation?.isReported)
                val request = fakeReport.requests.single()
                assertEquals(OutputReportReason.WRONG, request.reason)
                assertEquals("The styled words.", request.styledOutput)
                assertEquals("the dictated words", request.sourceTranscript)
                assertEquals("seneca", request.styleId)
                assertTrue(request.appVersion.contains("("))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a second reason tap while submitting does not resubmit`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeLastResult.record(sampleLastResult())
            val gate = CompletableDeferred<Unit>()
            fakeReport.gate = gate

            viewModel.uiState.test {
                viewModel.onReportClick()
                awaitState { it.report.pickerOpen }

                viewModel.onReportReasonClick(ReportReasonUi.OFFENSIVE)
                awaitState { it.report.isSubmitting }

                viewModel.onReportReasonClick(ReportReasonUi.OFFENSIVE)
                assertEquals(1, fakeReport.requests.size)

                gate.complete(Unit)
                awaitState { it.report.reportSent }
                assertEquals(1, fakeReport.requests.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed report keeps the picker open and the retry reuses the same report id`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeLastResult.record(sampleLastResult())
            fakeReport.outcome = OutputReportOutcome.Unavailable

            viewModel.uiState.test {
                viewModel.onReportClick()
                awaitState { it.report.pickerOpen }

                viewModel.onReportReasonClick(ReportReasonUi.OTHER)
                val failed = awaitState { it.report.error == MSG_REPORT_UNAVAILABLE }

                assertTrue(failed.report.pickerOpen)

                fakeReport.outcome = OutputReportOutcome.Submitted(duplicate = false)
                viewModel.onReportReasonClick(ReportReasonUi.OTHER)
                val state = awaitState { it.report.reportSent }
                assertEquals(2, fakeReport.requests.size)
                assertEquals(
                    fakeReport.requests[0].clientReportId,
                    fakeReport.requests[1].clientReportId,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a signed-out report outcome shows its error in the picker`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            fakeLastResult.record(sampleLastResult())
            fakeReport.outcome = OutputReportOutcome.SignedOut

            viewModel.uiState.test {
                viewModel.onReportClick()
                awaitState { it.report.pickerOpen }

                viewModel.onReportReasonClick(ReportReasonUi.SEXUAL)
                val state = awaitState { it.report.error == MSG_REPORT_SIGNED_OUT }

                assertTrue(state.report.pickerOpen)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
