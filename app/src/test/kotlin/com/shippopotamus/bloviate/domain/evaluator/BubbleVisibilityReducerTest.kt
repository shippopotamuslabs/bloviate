package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BubbleVisibilityReducerTest {

    private val reducer = BubbleVisibilityReducer()

    @Test
    fun `editable focus shows the bubble, and repeats do not re-command`() {
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))
        // Moving between two editable fields is still "visible" — no new command.
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_B)))
    }

    @Test
    fun `losing editable focus hides the bubble, and repeats do not re-command`() {
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.NoEditableFocus),
        )
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
    }

    @Test
    fun `password focus hides the bubble`() {
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A)),
        )
        // And the bubble comes back for the next editable field.
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )
    }

    @Test
    fun `password focus while hidden commands nothing`() {
        assertNull(reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A)))
    }

    @Test
    fun `dismiss hides the bubble and suppresses it until focus leaves editable and returns`() {
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onBubbleDismissed())

        // Same continuous editable session — even in another app — stays dismissed.
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_B)))

        // Focus leaves editable fields: suppression clears (nothing to hide).
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))

        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_B)),
        )
    }

    @Test
    fun `dismiss clears suppression when focus detours through a password field`() {
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        assertNull(reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A)))
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )
    }

    @Test
    fun `dismiss while hidden still suppresses the next editable focus`() {
        assertNull(reducer.onBubbleDismissed())
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))
    }

    @Test
    fun `reset forgets visibility and dismissal`() {
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        reducer.reset()

        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )
    }

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
