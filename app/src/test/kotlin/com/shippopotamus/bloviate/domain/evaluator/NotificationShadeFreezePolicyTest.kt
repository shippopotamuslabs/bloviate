package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.WatcherWindowSnapshot
import com.shippopotamus.bloviate.domain.model.WatcherWindowSnapshot.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationShadeFreezePolicyTest {

    // --- Shade presence ------------------------------------------------------

    @Test
    fun `an active system window is the notification shade`() {
        assertTrue(
            NotificationShadeFreezePolicy.isShadePresent(
                listOf(
                    window(Kind.APPLICATION, isActive = false),
                    window(Kind.SYSTEM, isActive = true),
                    window(Kind.INPUT_METHOD, isActive = false),
                ),
            ),
        )
    }

    @Test
    fun `an inactive system window (the status bar) is not the shade`() {
        assertFalse(
            NotificationShadeFreezePolicy.isShadePresent(
                listOf(
                    window(Kind.APPLICATION, isActive = true),
                    window(Kind.SYSTEM, isActive = false),
                ),
            ),
        )
    }

    @Test
    fun `an active application or input-method window is not the shade`() {
        assertFalse(
            NotificationShadeFreezePolicy.isShadePresent(
                listOf(
                    window(Kind.APPLICATION, isActive = true),
                    window(Kind.INPUT_METHOD, isActive = false),
                ),
            ),
        )
        assertFalse(
            NotificationShadeFreezePolicy.isShadePresent(
                listOf(window(Kind.INPUT_METHOD, isActive = true)),
            ),
        )
    }

    @Test
    fun `no windows means no shade`() {
        assertFalse(NotificationShadeFreezePolicy.isShadePresent(emptyList()))
    }

    // --- Early shade event (ENC-156) -------------------------------------------

    @Test
    fun `a systemui window-state event is the shade one event before the census`() {
        assertTrue(
            NotificationShadeFreezePolicy.isShadeEvent(
                packageName = "com.android.systemui",
                isWindowStateChange = true,
            ),
        )
    }

    @Test
    fun `a host app window-state event is not the shade`() {
        assertFalse(
            NotificationShadeFreezePolicy.isShadeEvent(
                packageName = PACKAGE,
                isWindowStateChange = true,
            ),
        )
    }

    @Test
    fun `systemui content churn is not the shade - only window-state events qualify`() {
        assertFalse(
            NotificationShadeFreezePolicy.isShadeEvent(
                packageName = "com.android.systemui",
                isWindowStateChange = false,
            ),
        )
    }

    @Test
    fun `a null package is not the shade`() {
        assertFalse(
            NotificationShadeFreezePolicy.isShadeEvent(
                packageName = null,
                isWindowStateChange = true,
            ),
        )
    }

    // --- Focus gate ----------------------------------------------------------

    @Test
    fun `shade opening mid-recording holds the focus signal, so no hide command is emitted`() {
        // The shade's tree contains no editable node, so the derivation
        // flips to NoEditableFocus — the exact signal that would issue
        // an instant focus-path hide and kill the recording. The gate
        // holds it: nothing is published, the session is untouched.
        assertNull(
            NotificationShadeFreezePolicy.gateFocus(
                shadePresent = true,
                derived = FieldFocusState.NoEditableFocus,
            ),
        )
    }

    @Test
    fun `shade opening over an idle bubble holds the focus signal, so the bubble does not hide`() {
        assertNull(
            NotificationShadeFreezePolicy.gateFocus(
                shadePresent = true,
                derived = FieldFocusState.NoEditableFocus,
            ),
        )
        assertNull(
            NotificationShadeFreezePolicy.gateFocus(
                shadePresent = true,
                derived = FieldFocusState.EditableFocus(PACKAGE),
            ),
        )
    }

    @Test
    fun `without the shade every focus derivation passes through unchanged`() {
        assertEquals(
            FieldFocusState.EditableFocus(PACKAGE),
            NotificationShadeFreezePolicy.gateFocus(
                shadePresent = false,
                derived = FieldFocusState.EditableFocus(PACKAGE),
            ),
        )
        assertEquals(
            FieldFocusState.NoEditableFocus,
            NotificationShadeFreezePolicy.gateFocus(
                shadePresent = false,
                derived = FieldFocusState.NoEditableFocus,
            ),
        )
    }

    @Test
    fun `shade closing re-derives immediately, and a meanwhile password focus hides instantly`() {
        // Full sequence: editable focus held, shade opens (frozen),
        // a password field takes focus behind the shade, shade closes.
        var published: FieldFocusState = FieldFocusState.EditableFocus(PACKAGE)

        val duringShade = NotificationShadeFreezePolicy.gateFocus(
            shadePresent = true,
            derived = FieldFocusState.NoEditableFocus,
        )
        if (duringShade != null) published = duringShade
        assertEquals(FieldFocusState.EditableFocus(PACKAGE), published)

        val afterShade = NotificationShadeFreezePolicy.gateFocus(
            shadePresent = false,
            derived = FieldFocusState.PasswordFocus(PACKAGE),
        )
        if (afterShade != null) published = afterShade
        // No debounce, no grace period: the password hide is the very
        // first signal after the shade leaves.
        assertEquals(FieldFocusState.PasswordFocus(PACKAGE), published)
    }

    // --- Keyboard gate -------------------------------------------------------

    @Test
    fun `shade opening holds the keyboard signal in both directions`() {
        assertNull(NotificationShadeFreezePolicy.gateKeyboard(shadePresent = true, derivedVisible = true))
        assertNull(NotificationShadeFreezePolicy.gateKeyboard(shadePresent = true, derivedVisible = false))
    }

    @Test
    fun `without the shade the keyboard derivation passes through unchanged`() {
        assertEquals(true, NotificationShadeFreezePolicy.gateKeyboard(shadePresent = false, derivedVisible = true))
        assertEquals(false, NotificationShadeFreezePolicy.gateKeyboard(shadePresent = false, derivedVisible = false))
    }

    private fun window(kind: Kind, isActive: Boolean): WatcherWindowSnapshot =
        WatcherWindowSnapshot(kind = kind, isActive = isActive)

    private companion object {
        const val PACKAGE = "com.example.host"
    }
}
