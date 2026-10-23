package com.shippopotamus.bloviate.service

import android.view.View
import android.view.WindowManager

/**
 * Resident bubble window (ENC-163): the overlay window is added ONCE via
 * `WindowManager.addView` and stays added for the service's lifetime.
 * The view stays [View.VISIBLE] at all times so the platform keeps
 * treating the window as shown (removing the view drops the "displaying
 * over other apps" system notice whenever the bubble hides); hidden is
 * `LayoutParams.alpha = 0f` plus `FLAG_NOT_TOUCHABLE`, shown is
 * `alpha = 1f` with that flag cleared — so the composition (and any
 * in-flight recording bar, ENC-156) survives every hide.
 * `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS` are the base flags in
 * both states (`FLAG_NOT_FOCUSABLE` is non-negotiable per
 * `ARCHITECTURE_RULES.md` §5.2: a focusable overlay steals the host
 * field's caret and the bubble hides itself). Window-level alpha 0
 * renders no visible pixels, and taps pass through via
 * `NOT_TOUCHABLE`.
 *
 * The notification shade never touches this window (ENC-164): it stays
 * exactly as commanded while the shade is up, so the bubble persists
 * through the shade and recording is untouched. The shade is a
 * deterministic no-op enforced at the point of effect in
 * `BubbleOverlayService`, not here.
 *
 * The window gateway lambdas mirror [BubbleWindowPlacement]'s `pushLayout`
 * pattern, so add/remove/update/visibility/flags are fakeable in unit
 * tests without a real `WindowManager`.
 *
 * Removal happens only through [removeIfPermissionLost] (overlay
 * permission is poll-only via `canDrawOverlays`) or [detach] (service
 * teardown): never across show/hide cycles and never across
 * rotation/fold (the resident window re-clamps in place). Removal
 * fires no host-hidden side effect.
 */
class ResidentBubbleWindow(
    private val canDrawOverlays: () -> Boolean,
    private val addView: (View, WindowManager.LayoutParams) -> Unit,
    private val removeView: (View) -> Unit,
    private val updateView: (View, WindowManager.LayoutParams) -> Unit,
    private val onHostHidden: (isPasswordFocus: Boolean) -> Unit,
    private val isPasswordFocus: () -> Boolean,
) {

    /** The attached view, or null once [detach] has run. */
    var attachedView: View? = null
        private set

    /** The attached params, or null once [detach] has run. */
    var attachedParams: WindowManager.LayoutParams? = null
        private set

    /** True once [ensureAttached] has added the window and before [detach] removes it. */
    val isAttached: Boolean get() = attachedView != null

    /**
     * What is actually on screen ([setVisible]). Starts hidden.
     */
    var isVisible: Boolean = false
        private set

    /**
     * Adds the window if permission is present and it is not already
     * added. The window attaches already-hidden in the alpha sense
     * (`VISIBLE` view, alpha 0, untouchable) so a fresh attach never
     * flashes; the caller shows it with [setVisible]. Idempotent: a
     * re-attach while attached is a no-op returning true. Returns
     * false (adding nothing) when permission is missing.
     */
    fun ensureAttached(view: View, params: WindowManager.LayoutParams): Boolean {
        if (isAttached) return true
        if (!canDrawOverlays()) return false
        view.visibility = View.VISIBLE
        params.alpha = HIDDEN_ALPHA
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        addView(view, params)
        attachedView = view
        attachedParams = params
        isVisible = false
        return true
    }

    /**
     * Commands the visibility. No-ops when detached or when already in
     * the requested state. The host-hidden side effect fires only on an
     * actual visible-to-hidden transition — never on removal
     * ([detach]/[removeIfPermissionLost]) and never on repeated
     * hides. Returns true when the visibility transitioned.
     */
    fun setVisible(visible: Boolean): Boolean {
        if (!isAttached) return false
        if (visible == isVisible) return false
        isVisible = visible
        if (!visible) {
            onHostHidden(isPasswordFocus())
        }
        applyVisibility()
        return true
    }

    /**
     * Poll-point revocation check: when permission is gone but the
     * window is still added, removes it (no host-hidden side effect)
     * and reports true so the caller can tear down its composition
     * state. Otherwise a no-op returning false.
     */
    fun removeIfPermissionLost(): Boolean {
        if (!isAttached || canDrawOverlays()) return false
        detach()
        return true
    }

    /** Removes the window if attached, with no side effect. Idempotent. */
    fun detach() {
        val view = attachedView ?: return
        removeView(view)
        attachedView = null
        attachedParams = null
        isVisible = false
    }

    /**
     * Pushes the alpha/flags for the current [isVisible]. The view
     * stays `VISIBLE` in both states so the system notice survives
     * hides.
     */
    private fun applyVisibility() {
        val view = attachedView ?: return
        val params = attachedParams ?: return
        view.visibility = View.VISIBLE
        params.alpha = if (isVisible) SHOWN_ALPHA else HIDDEN_ALPHA
        params.flags = if (isVisible) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        // The base flags ride every params object from creation and are
        // only ever OR-ed here, so they survive both transitions; re-OR
        // them defensively so a caller-mutated params cannot lose focus
        // protection silently.
        params.flags = params.flags or BASE_FLAGS
        updateView(view, params)
    }

    private companion object {
        /** Base flags preserved in both visibility states (contract §5.2). */
        const val BASE_FLAGS: Int =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        /** Window-level alpha while hidden: renders no visible pixels, keeps the window shown. */
        const val HIDDEN_ALPHA: Float = 0f

        /** Window-level alpha while shown. */
        const val SHOWN_ALPHA: Float = 1f
    }
}
