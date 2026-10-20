package com.shippopotamus.bloviate.domain.usecase

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.testing.FakeFieldFocusPort
import com.shippopotamus.bloviate.testing.FakeKeyboardVisibilityPort
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ObserveBubbleVisibilityUseCaseTest {

    private val fakeFieldFocusPort = FakeFieldFocusPort()
    private val fakeKeyboardVisibilityPort = FakeKeyboardVisibilityPort()
    private val useCase = ObserveBubbleVisibilityUseCase(
        fieldFocusPort = fakeFieldFocusPort,
        keyboardVisibilityPort = fakeKeyboardVisibilityPort,
        reducer = BubbleVisibilityReducer(),
    )

    @Test
    fun `focus transitions stream show and hide commands only`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // Equivalent visibility in another app produces no command.
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_B))
            expectNoEvents()

            fakeFieldFocusPort.publish(FieldFocusState.PasswordFocus(PACKAGE_B))
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            fakeFieldFocusPort.publish(FieldFocusState.NoEditableFocus)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keyboard transitions compose with focus in the same stream`() = runTest {
        useCase().test {
            // Focus first, keyboard down: nothing.
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            expectNoEvents()

            // Keyboard up over the focused field: show.
            fakeKeyboardVisibilityPort.publish(true)
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // Keyboard dismissed while the field keeps focus: hide.
            fakeKeyboardVisibilityPort.publish(false)
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            // Keyboard back: show again.
            fakeKeyboardVisibilityPort.publish(true)
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bar activity defers a keyboard hide until the bar settles`() = runTest {
        useCase().test {
            fakeKeyboardVisibilityPort.publish(true)
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            assertNull(useCase.onBarActivityChanged(true))

            // Keyboard hides mid-recording: no command, the hide defers.
            fakeKeyboardVisibilityPort.publish(false)
            advanceUntilIdle()
            expectNoEvents()

            // The bar settling returns the deferred hide for the service.
            assertEquals(BubbleVisibilityCommand.HideBubble, useCase.onBarActivityChanged(false))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss routes through the same state machine as focus transitions`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            assertEquals(BubbleVisibilityCommand.HideBubble, useCase.onBubbleDismissed())

            // Suppressed for the rest of this editable session...
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_B))
            expectNoEvents()

            // ...and back for the next one, proving the stream is still live.
            // advanceUntilIdle forces the StateFlow collector to process the
            // intermediate NoEditableFocus instead of conflating it away.
            fakeFieldFocusPort.publish(FieldFocusState.NoEditableFocus)
            advanceUntilIdle()
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_B))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss while hidden returns no command`() {
        assertNull(useCase.onBubbleDismissed())
    }

    // --- Keyboard-hide hysteresis (ENC-127) ---------------------------------

    @Test
    fun `a keyboard flap inside the hysteresis window never hides the bubble`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // Keyboard reported hidden, then visible again 300 ms later —
            // a window-churn flap, well inside the sustain window.
            fakeKeyboardVisibilityPort.publish(false)
            advanceTimeBy(300)
            fakeKeyboardVisibilityPort.publish(true)

            advanceUntilIdle()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sustained keyboard hide hides the bubble once the hysteresis elapses`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            fakeKeyboardVisibilityPort.publish(false)
            runCurrent()
            // Still inside the sustain window: the hide has not landed.
            advanceTimeBy(499)
            expectNoEvents()

            advanceTimeBy(1)
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a password focus hides immediately while a keyboard hide is still sustaining`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // The keyboard hide starts sustaining; before it can land,
            // a password field takes focus — that hide is NOT debounced
            // (the mid-recording password cancel depends on it).
            fakeKeyboardVisibilityPort.publish(false)
            runCurrent()
            fakeFieldFocusPort.publish(FieldFocusState.PasswordFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            // The sustained keyboard hide lands later and owes nothing more.
            advanceUntilIdle()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Window-presence reconciliation (ENC-127) ---------------------------

    @Test
    fun `a swallowed show re-shows on the next focus transition after the presence resync`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // The service applied the show but no window materialized;
            // its presence report resyncs the machine's belief.
            assertNull(useCase.onWindowPresenceChanged(false))

            // The next focus transition commands a fresh show instead of
            // being swallowed by the stale belief.
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_B))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
