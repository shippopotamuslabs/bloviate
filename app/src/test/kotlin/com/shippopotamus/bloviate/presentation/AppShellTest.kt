package com.shippopotamus.bloviate.presentation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The shell's one layout decision (ENC-124): the tab bar reserves its
 * slab of the column only while no IME is up. With a keyboard open, a
 * reserved bar slab would stack on top of the screens' `imePadding()`
 * (measured from the window's bottom edge) and leave a dead gap
 * between the content and the keyboard — the Style tab is where
 * Sean photographed it. The inset rendering itself
 * is verified on Sean's device; this pins the decision the shell
 * feeds it.
 */
class AppShellTest {
    @Test
    fun `tab bar reserves its slab while the keyboard is closed`() {
        assertTrue(tabBarVisible(imeVisible = false))
    }

    @Test
    fun `tab bar leaves the layout while the keyboard is open`() {
        assertFalse(tabBarVisible(imeVisible = true))
    }
}
