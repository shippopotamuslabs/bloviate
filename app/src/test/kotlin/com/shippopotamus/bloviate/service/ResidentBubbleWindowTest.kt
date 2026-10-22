package com.shippopotamus.bloviate.service

import android.view.View
import android.view.WindowManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ENC-163: the resident window is added once and toggled by
 * visibility only. Removal happens only on teardown or overlay
 * permission revocation — never across show/hide cycles, and never
 * across rotation/fold.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ResidentBubbleWindowTest {

    private var canDrawOverlays: Boolean = true
    private var passwordFocus: Boolean = false
    private var addCalls: Int = 0
    private var removeCalls: Int = 0
    private val updateFlags = mutableListOf<Int>()
    private val hostHiddenCalls = mutableListOf<Boolean>()

    private lateinit var view: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var window: ResidentBubbleWindow

    @BeforeEach
    fun setup() {
        view = View(RuntimeEnvironment.getApplication())
        params = WindowManager.LayoutParams().apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        window = ResidentBubbleWindow(
            canDrawOverlays = { canDrawOverlays },
            addView = { _, _ -> addCalls++ },
            removeView = { _ -> removeCalls++ },
            updateView = { _, pushed -> updateFlags += pushed.flags },
            onHostHidden = { isPasswordFocus -> hostHiddenCalls += isPasswordFocus },
            isPasswordFocus = { passwordFocus },
        )
    }

    @Test
    fun `repeated show-hide cycles add the window exactly once and never remove it`() {
        assertTrue(window.ensureAttached(view, params))
        // A re-attach while attached is a no-op returning true.
        assertTrue(window.ensureAttached(view, params))

        repeat(5) {
            assertTrue(window.setVisible(true))
            assertTrue(window.setVisible(false))
        }

        assertEquals(1, addCalls)
        assertEquals(0, removeCalls)
        assertTrue(window.isAttached)
        assertFalse(window.isVisible)
    }

    @Test
    fun `hidden state stays VISIBLE at alpha 0 with NOT_TOUCHABLE set, shown is alpha 1 while base flags stay`() {
        window.ensureAttached(view, params)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(0f, params.alpha)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0)

        window.setVisible(true)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, params.alpha)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0)

        window.setVisible(false)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(0f, params.alpha)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0)
    }

    @Test
    fun `permission loss while attached removes exactly once, and return plus show re-adds once`() {
        window.ensureAttached(view, params)
        window.setVisible(true)

        canDrawOverlays = false
        assertTrue(window.removeIfPermissionLost())
        assertEquals(1, removeCalls)
        // A second poll is a no-op — exactly one remove.
        assertFalse(window.removeIfPermissionLost())
        assertEquals(1, removeCalls)
        assertFalse(window.isAttached)

        canDrawOverlays = true
        assertTrue(window.ensureAttached(view, params))
        assertEquals(2, addCalls)
        assertTrue(window.setVisible(true))
        assertTrue(window.isVisible)
    }

    @Test
    fun `attach adds nothing when permission is missing`() {
        canDrawOverlays = false

        assertFalse(window.ensureAttached(view, params))

        assertEquals(0, addCalls)
        assertFalse(window.isAttached)
    }

    @Test
    fun `visibility toggles and permission polls update the layout without adding or removing`() {
        window.ensureAttached(view, params)

        window.setVisible(true)
        window.setVisible(false)
        // The granted poll (the rotation/fold path when permission
        // holds) touches nothing.
        assertFalse(window.removeIfPermissionLost())

        assertEquals(1, addCalls)
        assertEquals(0, removeCalls)
        // One layout update per transition, starting hidden for free
        // (the hidden flags ride the add, not an update).
        assertEquals(listOf(showFlags(), hideFlags()), updateFlags)
    }

    @Test
    fun `host-hidden side effect fires once per visible-to-hidden transition`() {
        // Hides with no window, or with an already-hidden window, fire nothing.
        assertFalse(window.setVisible(false))
        window.ensureAttached(view, params)
        assertFalse(window.setVisible(false))
        assertTrue(hostHiddenCalls.isEmpty())

        window.setVisible(true)
        passwordFocus = false
        assertTrue(window.setVisible(false))
        assertEquals(listOf(false), hostHiddenCalls)

        // A repeated hide is not a transition — no replay.
        assertFalse(window.setVisible(false))
        assertEquals(1, hostHiddenCalls.size)

        // Removal is not a hide — no side effect.
        window.setVisible(true)
        window.detach()
        assertEquals(1, hostHiddenCalls.size)

        // The next generation forwards the live password cause.
        window.ensureAttached(view, params)
        window.setVisible(true)
        passwordFocus = true
        window.setVisible(false)
        assertEquals(listOf(false, true), hostHiddenCalls)
    }

    private fun showFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun hideFlags(): Int =
        showFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
}
