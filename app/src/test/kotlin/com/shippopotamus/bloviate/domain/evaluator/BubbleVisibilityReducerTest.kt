package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BubbleVisibilityReducerTest {

    private val reducer = BubbleVisibilityReducer()

    // --- Focus composition (keyboard already up) ----------------------------

    @Test
    fun `editable focus shows the bubble, and repeats do not re-command`() {
        reducer.onKeyboardVisibilityChanged(true)

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
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.NoEditableFocus),
        )
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
    }

    @Test
    fun `password focus hides the bubble`() {
        reducer.onKeyboardVisibilityChanged(true)
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
        reducer.onKeyboardVisibilityChanged(true)

        assertNull(reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A)))
    }

    @Test
    fun `dismiss hides the bubble and suppresses it until focus leaves editable and returns`() {
        reducer.onKeyboardVisibilityChanged(true)
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
        reducer.onKeyboardVisibilityChanged(true)
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
        reducer.onKeyboardVisibilityChanged(true)

        assertNull(reducer.onBubbleDismissed())
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))
    }

    @Test
    fun `reset forgets visibility, dismissal, and keyboard state`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        reducer.reset()

        // Keyboard state was forgotten too: focus alone no longer shows.
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))
        assertEquals(BubbleVisibilityCommand.ShowBubble, reducer.onKeyboardVisibilityChanged(true))
    }

    // --- Keyboard composition (ENC-125) -------------------------------------

    @Test
    fun `editable focus with the keyboard down shows nothing until the keyboard appears`() {
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))

        assertEquals(BubbleVisibilityCommand.ShowBubble, reducer.onKeyboardVisibilityChanged(true))
        // A repeated keyboard-visible report does not re-command.
        assertNull(reducer.onKeyboardVisibilityChanged(true))
    }

    @Test
    fun `keyboard appearing with no editable focus shows nothing`() {
        assertNull(reducer.onKeyboardVisibilityChanged(true))
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
    }

    @Test
    fun `keyboard hiding hides the bubble even though the field keeps focus, and its return shows it again`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onKeyboardVisibilityChanged(false))
        // A repeated keyboard-hidden report does not re-command.
        assertNull(reducer.onKeyboardVisibilityChanged(false))

        assertEquals(BubbleVisibilityCommand.ShowBubble, reducer.onKeyboardVisibilityChanged(true))
    }

    @Test
    fun `dismissal survives a keyboard cycle within the same field session`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        assertNull(reducer.onKeyboardVisibilityChanged(false))
        // The keyboard returning does not resurrect a dismissed bubble.
        assertNull(reducer.onKeyboardVisibilityChanged(true))
    }

    // --- Bar-activity exemption (ENC-125) -----------------------------------

    @Test
    fun `keyboard hide during bar activity defers, and the hide lands when the bar settles`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)

        assertNull(reducer.onKeyboardVisibilityChanged(false))

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onBarActivityChanged(false))
    }

    @Test
    fun `keyboard returning before the bar settles cancels the deferred hide`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)
        reducer.onKeyboardVisibilityChanged(false)

        assertNull(reducer.onKeyboardVisibilityChanged(true))
        assertNull(reducer.onBarActivityChanged(false))
    }

    @Test
    fun `password focus during bar activity hides immediately despite a deferred keyboard hide`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)
        reducer.onKeyboardVisibilityChanged(false)

        // The mid-recording password cancel is not exempted: hide now.
        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A)),
        )
        // And the bar settling afterwards owes nothing more.
        assertNull(reducer.onBarActivityChanged(false))
    }

    @Test
    fun `focus loss during bar activity hides immediately - the exemption is keyboard-only`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)

        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.NoEditableFocus),
        )
        assertNull(reducer.onBarActivityChanged(false))
    }

    @Test
    fun `bar activity toggling while the bubble is hidden commands nothing`() {
        reducer.onKeyboardVisibilityChanged(true)

        assertNull(reducer.onBarActivityChanged(true))
        assertNull(reducer.onBarActivityChanged(false))
    }

    // --- Window-presence reconciliation (ENC-127) ---------------------------

    @Test
    fun `a swallowed show resyncs on the presence report, and the next focus transition re-commands the show`() {
        reducer.onKeyboardVisibilityChanged(true)
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )

        // The service applied the show but no window materialized (e.g.
        // overlay permission was missing at apply time): the report
        // resyncs the belief silently — no command here...
        assertNull(reducer.onWindowPresenceChanged(false))

        // ...so the next show-worthy transition commands a fresh show
        // instead of being swallowed by the stale "already visible".
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_B)),
        )
    }

    @Test
    fun `presence reports that agree with the belief command nothing`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertNull(reducer.onWindowPresenceChanged(true))

        reducer.onFocusState(FieldFocusState.NoEditableFocus)
        assertNull(reducer.onWindowPresenceChanged(false))
    }

    @Test
    fun `a window present while nothing is desired gets a corrective hide`() {
        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onWindowPresenceChanged(true))
        // After the correction the belief agrees with the (now removed) window.
        assertNull(reducer.onWindowPresenceChanged(false))
    }

    @Test
    fun `a window present while the bubble is dismissed gets a corrective hide`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onWindowPresenceChanged(true))
    }

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
