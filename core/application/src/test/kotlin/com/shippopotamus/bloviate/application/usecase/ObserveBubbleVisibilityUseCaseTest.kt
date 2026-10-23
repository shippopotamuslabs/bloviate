package com.shippopotamus.bloviate.application.usecase

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `the hide cause follows the latest focus state`() = runTest {
        // ENC-156: the overlay reads this at hide time — only a real
        // password focus may cancel the recording.
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())
            assertFalse(useCase.isPasswordFocus())

            fakeFieldFocusPort.publish(FieldFocusState.NoEditableFocus)
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())
            assertFalse(useCase.isPasswordFocus())

            fakeFieldFocusPort.publish(FieldFocusState.PasswordFocus(PACKAGE_A))
            expectNoEvents()
            assertTrue(useCase.isPasswordFocus())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Keyboard hides are immediate again (ENC-128) -----------------------
    //
    // ENC-127 sustained a keyboard `false` for 500 ms before forwarding
    // it, insuring against a flapping-keyboard class that was inferred
    // but never observed. ENC-128's event-starvation model explains the
    // field reports without a flap, and the watcher's watchdog
    // re-derivation owns convergence in both directions — so the sustain
    // was removed with ENC-128. These pins record the new contract: a
    // keyboard hide lands at once, and a transient mis-reading heals by
    // re-showing on the next (watchdog-supplied) transition, not by
    // delaying every real dismissal.

    @Test
    fun `a keyboard hide lands immediately, with no sustain`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            fakeKeyboardVisibilityPort.publish(false)
            runCurrent()
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a transient keyboard mis-reading self-corrects on the next transition`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // A momentary false reading hides at once — and the
            // re-derived true that follows (in the field, the watchdog's
            // next poll) restores the bubble just as promptly. The pair
            // converges; nothing sticks in either direction.
            fakeKeyboardVisibilityPort.publish(false)
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())
            fakeKeyboardVisibilityPort.publish(true)
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a password focus hides immediately, in the same beat as any keyboard transition`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // The password hide was never debounced and never will be —
            // the mid-recording password cancel depends on it landing
            // with no time advance at all.
            fakeFieldFocusPort.publish(FieldFocusState.PasswordFocus(PACKAGE_A))
            runCurrent()
            assertEquals(BubbleVisibilityCommand.HideBubble, awaitItem())

            advanceUntilIdle()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Window-visibility reconciliation (ENC-127) ---------------------------

    @Test
    fun `a swallowed show re-shows on the next focus transition after the visibility resync`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // The service applied the show but no window materialized;
            // its visibility report resyncs the machine's belief.
            assertNull(useCase.onWindowVisibilityChanged(false))

            // The next focus transition commands a fresh show instead of
            // being swallowed by the stale belief.
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_B))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Event-starved keyboard derivation (ENC-128) ------------------------

    @Test
    fun `an event-starved sequence converges to show when the watchdog re-derives the keyboard`() = runTest {
        useCase().test {
            // Notification entry: the field is restored already-focused
            // while the watcher's last derivation read keyboard = false —
            // the IME window had not joined the window list at any event.
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            expectNoEvents()

            // The tap lands; its re-derivation still enumerates a window
            // list without the IME, and no further event ever fires. Time
            // passes: without the watchdog this state is terminal.
            advanceTimeBy(2_000)
            expectNoEvents()

            // A watchdog poll re-derives from the live window list, where
            // the IME window has silently joined: the keyboard transition
            // publishes, and the machine converges to a show.
            fakeKeyboardVisibilityPort.publish(true)
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the notification entry path shows once the silently joined keyboard is re-derived`() = runTest {
        useCase().test {
            // Service (re)connect on the entry path: initial derivation
            // publishes keyboard = false before the field is focused.
            fakeKeyboardVisibilityPort.publish(false)
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            expectNoEvents()

            // Entry completes; the in-app conversation switch in Sean's
            // repro manufactured the events that healed this — the
            // watchdog's re-derivation now supplies the same transition
            // without the user having to navigate anywhere.
            fakeKeyboardVisibilityPort.publish(true)
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a converged state stays silent, with no watchdog-shaped activity in the pipeline`() = runTest {
        fakeKeyboardVisibilityPort.publish(true)
        useCase().test {
            fakeFieldFocusPort.publish(FieldFocusState.EditableFocus(PACKAGE_A))
            assertEquals(BubbleVisibilityCommand.ShowBubble, awaitItem())

            // Converged (bubble shown, keyboard up): the watchdog never
            // arms here, and time passing produces nothing.
            advanceTimeBy(5_000)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
