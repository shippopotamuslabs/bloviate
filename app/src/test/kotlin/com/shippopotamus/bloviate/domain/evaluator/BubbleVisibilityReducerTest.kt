package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleVisibilityCommand
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `the decision snapshot names the focus and keyboard signals`() {
        // ENC-145: one repro line identifies the stuck signal.
        val idle = reducer.describeStateForLog()
        assertTrue(idle.contains("focus=NoEditableFocus"))
        assertTrue(idle.contains("keyboardVisible=false"))
        assertTrue(idle.contains("bubbleVisible=false"))

        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        val showing = reducer.describeStateForLog()
        assertTrue(showing.contains("EditableFocus"))
        assertTrue(showing.contains("keyboardVisible=true"))
        assertTrue(showing.contains("bubbleVisible=true"))
    }

    @Test
    fun `only a password focus reads as a password hide`() {
        // ENC-156: the sink hardens on this — a shade-peek focus-loss
        // must never cancel a recording.
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        assertFalse(reducer.isPasswordFocus())

        reducer.onFocusState(FieldFocusState.NoEditableFocus)
        assertFalse(reducer.isPasswordFocus())

        reducer.onFocusState(FieldFocusState.PasswordFocus(PACKAGE_A))
        assertTrue(reducer.isPasswordFocus())

        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        assertFalse(reducer.isPasswordFocus())
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
    fun `focus loss during bar activity defers, and the hide lands when the bar settles`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)

        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onBarActivityChanged(false))
    }

    @Test
    fun `focus loss during bar activity then editable refocus hides nothing at settle`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))

        // Editable focus returns with the keyboard still up before the
        // bar settles: the bubble is desired again, so the settle owes
        // no hide.
        assertNull(reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)))
        assertNull(reducer.onBarActivityChanged(false))
    }

    @Test
    fun `focus loss plus keyboard hide during bar activity lands a single deferred hide`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBarActivityChanged(true)
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
        assertNull(reducer.onKeyboardVisibilityChanged(false))

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onBarActivityChanged(false))
        // No duplicate owed afterwards.
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
    }

    @Test
    fun `bar activity toggling while the bubble is hidden commands nothing`() {
        reducer.onKeyboardVisibilityChanged(true)

        assertNull(reducer.onBarActivityChanged(true))
        assertNull(reducer.onBarActivityChanged(false))
    }

    // --- Window-visibility reconciliation (ENC-127) ---------------------------

    @Test
    fun `a swallowed show resyncs on the visibility report, and the next focus transition re-commands the show`() {
        reducer.onKeyboardVisibilityChanged(true)
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )

        // The service applied the show but no window materialized (e.g.
        // overlay permission was missing at apply time): the report
        // resyncs the belief silently — no command here...
        assertNull(reducer.onWindowVisibilityChanged(false))

        // ...so the next show-worthy transition commands a fresh show
        // instead of being swallowed by the stale "already visible".
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_B)),
        )
    }

    @Test
    fun `visibility reports that agree with the belief command nothing`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))

        assertNull(reducer.onWindowVisibilityChanged(true))

        reducer.onFocusState(FieldFocusState.NoEditableFocus)
        assertNull(reducer.onWindowVisibilityChanged(false))
    }

    @Test
    fun `a window shown while nothing is desired gets a corrective hide`() {
        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onWindowVisibilityChanged(true))
        // After the correction the belief agrees with the (now removed) window.
        assertNull(reducer.onWindowVisibilityChanged(false))
    }

    @Test
    fun `a shade-swallowed show resyncs on the lift report with no corrective, and the next hide still works`() {
        // ENC-164: the show was swallowed at the point of effect while
        // the shade was up, so the reconcile report flipped the belief
        // back to hidden even though the signals still desire visible.
        reducer.onKeyboardVisibilityChanged(true)
        assertEquals(
            BubbleVisibilityCommand.ShowBubble,
            reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A)),
        )
        assertNull(reducer.onWindowVisibilityChanged(false))

        // The shade lifts with the window actually visible (it never
        // moved): the belief is restored silently — no corrective hide,
        // because the bubble is still desired.
        assertNull(reducer.onWindowVisibilityChanged(true))

        // And the next legitimate hide still commands exactly once.
        assertEquals(
            BubbleVisibilityCommand.HideBubble,
            reducer.onFocusState(FieldFocusState.NoEditableFocus),
        )
        assertNull(reducer.onFocusState(FieldFocusState.NoEditableFocus))
    }

    @Test
    fun `a window shown while the bubble is dismissed gets a corrective hide`() {
        reducer.onKeyboardVisibilityChanged(true)
        reducer.onFocusState(FieldFocusState.EditableFocus(PACKAGE_A))
        reducer.onBubbleDismissed()

        assertEquals(BubbleVisibilityCommand.HideBubble, reducer.onWindowVisibilityChanged(true))
    }

    private companion object {
        const val PACKAGE_A = "com.example.host.a"
        const val PACKAGE_B = "com.example.host.b"
    }
}
