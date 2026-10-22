package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.WatcherWindowSnapshot

/**
 * Pure policy for the notification-shade signal freeze (ENC-132).
 *
 * Pulling down the notification shade makes SystemUI's shade the
 * active window, so the watcher's focus derivation — which reads the
 * active window's tree — suddenly sees no editable node and flips to
 * [FieldFocusState.NoEditableFocus]. Under ENC-125's rules a
 * focus-loss hide is immediate and is NOT covered by the recording-bar
 * exemption (which defers keyboard-driven hides only), so a tray peek
 * instantly hid the bubble and killed any in-progress dictation.
 *
 * While the shade is physically present this policy freezes the
 * signals: the watcher holds the last published editor-focus and
 * keyboard states and emits nothing new. The moment the shade leaves
 * the window stream the freeze lifts and the caller re-derives from
 * live state, so real changes that happened behind the shade apply
 * immediately — including a password field focused meanwhile, whose
 * hide stays instant on that re-derivation. The freeze is bounded
 * strictly by the shade's physical presence, never by a timer.
 *
 * Shade detection: `AccessibilityWindowInfo` has no dedicated shade
 * type — SystemUI's shade is a system window, and while expanded it
 * is the *active* window (the source of input events), which is
 * exactly why the active-window derivation gets poisoned. An active
 * [WatcherWindowSnapshot.Kind.SYSTEM] window therefore marks the
 * shade; inactive system windows (e.g. the status bar) never freeze
 * anything. Ancillary system windows that take active status while
 * shown (e.g. the volume panel) freeze only for their own brief
 * presence, which is harmless for the same bounded reason.
 *
 * This policy touches neither the ENC-125 recording-bar exemption
 * (a reducer concern downstream of these signals) nor the ENC-128
 * watchdog semantics (the watchdog keeps its own arm/stop rules over
 * the held signals while frozen).
 */
object NotificationShadeFreezePolicy {

    /** True while a notification shade window is present in [windows]. */
    fun isShadePresent(windows: List<WatcherWindowSnapshot>): Boolean =
        windows.any { window ->
            window.kind == WatcherWindowSnapshot.Kind.SYSTEM && window.isActive
        }

    /**
     * True when this window-state event comes from SystemUI itself
     * (ENC-156). The shade's pull-down posts its window-state transition
     * before the window census reports an active system window, so the
     * census alone leaks exactly one poisoned derivation — read from
     * the shade's tree, not the host app's — which flips to
     * not-editable and kills any in-progress dictation through the
     * focus path. A SystemUI window-state event engages the freeze one
     * event earlier, on the transition itself. Only window-state events
     * qualify ([isWindowStateChange]): content churn from the
     * always-present status bar must never freeze the signals. The event
     * type arrives as a plain boolean so this policy stays free of
     * framework types.
     */
    fun isShadeEvent(packageName: String?, isWindowStateChange: Boolean): Boolean =
        isWindowStateChange &&
            packageName?.contains(SYSTEM_UI_MARKER, ignoreCase = true) == true

    /**
     * The focus signal to publish, or null to hold the last published
     * state because the shade is present and [derived] was read from
     * the shade's tree, not the host app's.
     */
    fun gateFocus(shadePresent: Boolean, derived: FieldFocusState): FieldFocusState? =
        if (shadePresent) null else derived

    /**
     * The keyboard signal to publish, or null to hold the last
     * published state because the shade is present.
     */
    fun gateKeyboard(shadePresent: Boolean, derivedVisible: Boolean): Boolean? =
        if (shadePresent) null else derivedVisible

    private const val SYSTEM_UI_MARKER: String = "systemui"
}
