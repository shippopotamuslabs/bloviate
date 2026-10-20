package com.shippopotamus.bloviate.domain.usecase

import app.cash.turbine.test
import com.shippopotamus.bloviate.domain.evaluator.BubbleVisibilityReducer
import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.testing.FakeFieldFocusPort
import com.shippopotamus.bloviate.testing.FakeKeyboardVisibilityPort
import kotlinx.coroutines.test.advanceUntilIdle
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

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
