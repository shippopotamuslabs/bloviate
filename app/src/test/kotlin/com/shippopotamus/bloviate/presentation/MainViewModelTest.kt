package com.shippopotamus.bloviate.presentation

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.usecase.IsFieldWatcherEnabledUseCase
import com.shippopotamus.bloviate.domain.usecase.IsOverlayPermissionGrantedUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveBubbleServiceRunningUseCase
import com.shippopotamus.bloviate.domain.usecase.ObserveFieldFocusUseCase
import com.shippopotamus.bloviate.testing.FakeBubbleOverlayAccessPort
import com.shippopotamus.bloviate.testing.FakeBubbleServiceStatePort
import com.shippopotamus.bloviate.testing.FakeFieldFocusPort
import com.shippopotamus.bloviate.testing.FakeWatcherAccessPort
import com.shippopotamus.bloviate.testing.MainDispatcherExtension
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
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setup() {
        viewModel = MainViewModel(
            observeFieldFocus = ObserveFieldFocusUseCase(fakeFieldFocusPort),
            isFieldWatcherEnabled = IsFieldWatcherEnabledUseCase(fakeWatcherAccessPort),
            isOverlayPermissionGranted = IsOverlayPermissionGrantedUseCase(fakeBubbleOverlayAccessPort),
            observeBubbleServiceRunning = ObserveBubbleServiceRunningUseCase(fakeBubbleServiceStatePort),
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
    fun `bubble service running state flows into ui state`() =
        runTest(mainDispatcherExtension.testDispatcher) {
            viewModel.uiState.test {
                assertFalse(awaitItem().bubbleServiceRunning)

                fakeBubbleServiceStatePort.publishServiceRunning(true)

                assertTrue(awaitItem().bubbleServiceRunning)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val PACKAGE_NAME = "com.example.host"
    }
}
