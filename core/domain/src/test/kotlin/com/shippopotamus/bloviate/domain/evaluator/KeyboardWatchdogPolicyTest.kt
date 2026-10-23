package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.evaluator.KeyboardWatchdogPolicy.StopReason
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardWatchdogPolicyTest {

    // --- The starved state ---------------------------------------------------

    @Test
    fun `starved is exactly editable focus held with the keyboard reading hidden`() {
        assertTrue(KeyboardWatchdogPolicy.isStarved(FieldFocusState.EditableFocus(PACKAGE), keyboardVisible = false))
        assertFalse(KeyboardWatchdogPolicy.isStarved(FieldFocusState.EditableFocus(PACKAGE), keyboardVisible = true))
    }

    @Test
    fun `no focus is never starved, so the watchdog never runs without an editable field`() {
        assertFalse(KeyboardWatchdogPolicy.isStarved(FieldFocusState.NoEditableFocus, keyboardVisible = false))
        assertFalse(KeyboardWatchdogPolicy.isStarved(FieldFocusState.NoEditableFocus, keyboardVisible = true))
    }

    @Test
    fun `a password field is never starved, so the watchdog can never arm or sustain on one`() {
        assertFalse(KeyboardWatchdogPolicy.isStarved(FieldFocusState.PasswordFocus(PACKAGE), keyboardVisible = false))
    }

    // --- Stop reasons --------------------------------------------------------

    @Test
    fun `a starved arm with time left keeps polling`() {
        assertNull(
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = false,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS - 1,
            ),
        )
    }

    @Test
    fun `the keyboard becoming visible stops the watchdog as converged`() {
        assertEquals(
            StopReason.KEYBOARD_VISIBLE,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = true,
                armedForMs = 0,
            ),
        )
    }

    @Test
    fun `focus leaving editable fields stops the watchdog`() {
        assertEquals(
            StopReason.FOCUS_LOST,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.NoEditableFocus,
                keyboardVisible = false,
                armedForMs = 0,
            ),
        )
    }

    @Test
    fun `a password field taking focus stops the watchdog immediately`() {
        assertEquals(
            StopReason.FOCUS_LOST,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.PasswordFocus(PACKAGE),
                keyboardVisible = false,
                armedForMs = 0,
            ),
        )
    }

    @Test
    fun `an arm that reaches the duration cap expires instead of polling forever`() {
        assertEquals(
            StopReason.EXPIRED,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = false,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS,
            ),
        )
    }

    @Test
    fun `convergence outranks expiry when both land on the same poll`() {
        assertEquals(
            StopReason.KEYBOARD_VISIBLE,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = true,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS,
            ),
        )
    }

    // --- The mirrored starved state (ENC-186) --------------------------------

    @Test
    fun `mirror starved is exactly no editable focus with the keyboard reading visible`() {
        assertTrue(KeyboardWatchdogPolicy.isMirrorStarved(FieldFocusState.NoEditableFocus, keyboardVisible = true))
        assertFalse(KeyboardWatchdogPolicy.isMirrorStarved(FieldFocusState.NoEditableFocus, keyboardVisible = false))
        assertFalse(KeyboardWatchdogPolicy.isMirrorStarved(FieldFocusState.EditableFocus(PACKAGE), keyboardVisible = true))
    }

    @Test
    fun `a password field is never mirror starved, so its visible keyboard arms nothing`() {
        assertFalse(KeyboardWatchdogPolicy.isMirrorStarved(FieldFocusState.PasswordFocus(PACKAGE), keyboardVisible = true))
    }

    @Test
    fun `a mirrored arm keeps polling while focus reads none and the keyboard is up`() {
        assertNull(
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.NoEditableFocus,
                keyboardVisible = true,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS - 1,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `a mirrored arm stops as focus regained when focus re-derives editable`() {
        assertEquals(
            StopReason.FOCUS_REGAINED,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = true,
                armedForMs = 0,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `mirrored convergence outranks expiry when both land on the same poll`() {
        assertEquals(
            StopReason.FOCUS_REGAINED,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.EditableFocus(PACKAGE),
                keyboardVisible = true,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `a mirrored arm stops as focus lost when the keyboard leaves`() {
        assertEquals(
            StopReason.FOCUS_LOST,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.NoEditableFocus,
                keyboardVisible = false,
                armedForMs = 0,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `a mirrored arm stops immediately when a password field takes focus`() {
        assertEquals(
            StopReason.FOCUS_LOST,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.PasswordFocus(PACKAGE),
                keyboardVisible = true,
                armedForMs = 0,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `a mirrored arm that never heals expires at the cap instead of polling forever`() {
        assertEquals(
            StopReason.EXPIRED,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.NoEditableFocus,
                keyboardVisible = true,
                armedForMs = KeyboardWatchdogPolicy.MAX_ARM_DURATION_MS,
                mirrored = true,
            ),
        )
    }

    @Test
    fun `the classic reading of the mirrored state is unchanged without the mirrored flag`() {
        // A classic evaluation of (no focus, keyboard visible) still reads
        // as converged: the mirror semantics exist only on a mirrored arm.
        assertEquals(
            StopReason.KEYBOARD_VISIBLE,
            KeyboardWatchdogPolicy.stopReason(
                focusState = FieldFocusState.NoEditableFocus,
                keyboardVisible = true,
                armedForMs = 0,
            ),
        )
    }

    private companion object {
        const val PACKAGE = "com.example.host"
    }
}
