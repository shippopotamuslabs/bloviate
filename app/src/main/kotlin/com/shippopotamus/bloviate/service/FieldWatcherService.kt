package com.shippopotamus.bloviate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.shippopotamus.bloviate.domain.evaluator.FieldFocusEvaluator
import com.shippopotamus.bloviate.domain.evaluator.KeyboardWatchdogPolicy
import com.shippopotamus.bloviate.domain.evaluator.NotificationShadeFreezePolicy
import com.shippopotamus.bloviate.domain.evaluator.TextSplicer
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.FocusedNodeSnapshot
import com.shippopotamus.bloviate.domain.model.WatcherWindowSnapshot
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
import com.shippopotamus.bloviate.domain.port.WatcherConnectionPort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches the focused input field system-wide and publishes a deduped
 * [FieldFocusState] through [FieldFocusPort] for the bubble overlay (ENC-59).
 * It also publishes keyboard visibility through [KeyboardVisibilityPort]
 * (ENC-125): an input-method window present in the accessibility window
 * stream means the on-screen keyboard is up. Only the window's type is
 * read for that signal — never its content.
 *
 * Every relevant event re-derives BOTH signals from the live window
 * state (ENC-127): the subscribed types include in-field interactions
 * (clicks, caret moves), so a tap inside an already-focused field after
 * a transient window-churn flap reconciles a stale keyboard reading
 * instead of leaving it stuck.
 *
 * Events alone are not always enough (ENC-128): entering an app by
 * tapping a notification can leave the derivation event-starved — the
 * IME window joins the window list after the entry sequence's last
 * event, and nothing ever re-derives. While the state is starved
 * (editable, non-password focus held; keyboard reading hidden) a
 * bounded watchdog re-derives on a timer per [KeyboardWatchdogPolicy]
 * and stops the moment the state converges. The transition log records
 * the window-type census and whether the reading came from an event or
 * a watchdog poll, so the two paths are distinguishable in logcat.
 *
 * The notification shade is a special case of the same derivation
 * hazard (ENC-132): while it is expanded, SystemUI's shade is the
 * active window, so the active-window tree contains no editable node
 * and the focus derivation would flip to not-editable — an instant
 * focus-path hide that kills the bubble and any in-progress dictation.
 * While a shade window is present in the window stream, both signals
 * are frozen per [NotificationShadeFreezePolicy]: the last published
 * states are held, nothing new is emitted, and the first event after
 * the shade leaves re-derives from live state (so a password field
 * focused meanwhile still hides instantly on that re-derivation).
 * Shade transitions are logged with the window-type census (types
 * only), so a shade repro is visible in logcat.
 *
 * Privacy contract: the watcher path reads only the host package name and
 * structural flags. Field text and content descriptions are never read,
 * logged, or stored for classification, and password fields classify as
 * hidden.
 *
 * The insertion path (ENC-62, [FocusedFieldOperator]) is the one deliberate
 * exception, and only at the user's explicit send tap: the paste action
 * reads no text at all; the `ACTION_SET_TEXT` splice fallback reads the
 * focused field's text and selection to splice around the cursor — the
 * behavior `contracts/ARCHITECTURE_RULES.md` §5.3 mandates — and never
 * logs or stores what it read. Every [AccessibilityNodeInfo] obtained
 * here is recycled before the call returns; no node state survives.
 */
@AndroidEntryPoint
class FieldWatcherService : AccessibilityService(), FocusedFieldOperator {

    @Inject
    lateinit var fieldFocusPort: FieldFocusPort

    @Inject
    lateinit var keyboardVisibilityPort: KeyboardVisibilityPort

    @Inject
    lateinit var watcherConnectionPort: WatcherConnectionPort

    @Inject
    lateinit var insertionBridge: FieldWatcherBridge

    @Inject
    lateinit var textSplicer: TextSplicer

    private var lastPublishedState: FieldFocusState = FieldFocusState.NoEditableFocus
    private var lastKeyboardVisible: Boolean = false

    // Notification-shade freeze state (ENC-132): while true, both
    // signals are held at their last published values and nothing new
    // is derived from the shade's window tree.
    private var shadePresent: Boolean = false

    // Keyboard-derivation watchdog state (ENC-128). Main-looper handler:
    // accessibility callbacks arrive on the main thread, and `windows`
    // is read there everywhere else in this service.
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogArmedAtMs: Long? = null
    private var watchdogPolls: Int = 0
    private val watchdogTick = Runnable { onWatchdogTick() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        insertionBridge.register(this)
        // Supervision (ENC-129): connection is a published fact, so an
        // enabled-but-disconnected watcher is visible in Setup and on
        // the bubble's notification instead of failing silently.
        watcherConnectionPort.publishConnected(true)
        publishKeyboardVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in RELEVANT_EVENT_TYPES) return
        // ENC-132: while the notification shade is present, hold both
        // signals — the active window is the shade's tree, so any
        // derivation here would be read from SystemUI, not the host
        // app. The first event after the shade leaves re-derives.
        // ENC-156: the shade's own window-state transition posts before
        // the census reports it, so the event itself also engages the
        // freeze — otherwise that first transition leaks exactly one
        // poisoned focus-loss through the census gap.
        if (updateShadePresence(windows, event)) return
        publish(FieldFocusEvaluator.evaluate(focusedNodeSnapshot(event)))
        publishKeyboardVisibility()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        insertionBridge.unregister(this)
        watcherConnectionPort.publishConnected(false)
        stopKeyboardWatchdog()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        insertionBridge.unregister(this)
        watcherConnectionPort.publishConnected(false)
        stopKeyboardWatchdog()
        publish(FieldFocusState.NoEditableFocus)
        keyboardVisibilityPort.publish(false)
        super.onDestroy()
    }

    override fun pasteIntoFocusedField(): FocusedFieldOutcome =
        withFocusedNode { node ->
            when {
                node.isPassword -> FocusedFieldOutcome.PASSWORD_FIELD
                !node.isEditableNode() -> FocusedFieldOutcome.NO_EDITABLE_FIELD
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE) -> FocusedFieldOutcome.COMPLETED
                else -> FocusedFieldOutcome.ACTION_UNSUPPORTED
            }
        }

    override fun spliceIntoFocusedField(insertion: String): FocusedFieldOutcome =
        withFocusedNode { node ->
            when {
                node.isPassword -> FocusedFieldOutcome.PASSWORD_FIELD
                !node.isEditableNode() -> FocusedFieldOutcome.NO_EDITABLE_FIELD
                else -> {
                    val spliced = textSplicer.splice(
                        currentText = node.text?.toString().orEmpty(),
                        selectionStart = node.textSelectionStart,
                        selectionEnd = node.textSelectionEnd,
                        insertion = insertion,
                    )
                    val setTextArguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            spliced.text,
                        )
                    }
                    if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArguments)) {
                        FocusedFieldOutcome.FAILED
                    } else {
                        // Best effort: leave the caret right after the
                        // inserted text, where the paste would have put it.
                        val selectionArguments = Bundle().apply {
                            putInt(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                                spliced.selectionStart,
                            )
                            putInt(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                                spliced.selectionEnd,
                            )
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArguments)
                        FocusedFieldOutcome.COMPLETED
                    }
                }
            }
        }

    /**
     * Runs [block] against the node holding input focus **right now**,
     * found fresh for this call, and recycles every node obtained. A
     * missing root or focus is [FocusedFieldOutcome.NO_EDITABLE_FIELD].
     */
    private fun withFocusedNode(block: (AccessibilityNodeInfo) -> FocusedFieldOutcome): FocusedFieldOutcome {
        val root = rootInActiveWindow ?: return FocusedFieldOutcome.NO_EDITABLE_FIELD
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return FocusedFieldOutcome.NO_EDITABLE_FIELD
            try {
                return block(focused)
            } finally {
                focused.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    /** The §5.1 editable rule: framework flag, or an `EditText` class name (WebView/legacy inputs lie). */
    private fun AccessibilityNodeInfo.isEditableNode(): Boolean =
        isEditable || className?.toString()?.contains(EDIT_TEXT_MARKER) == true

    private fun publish(state: FieldFocusState) {
        fieldFocusPort.publish(state)
        if (state != lastPublishedState) {
            lastPublishedState = state
            Log.d(TAG, "focus state -> ${state.describeForLog()}")
        }
        refreshKeyboardWatchdog()
    }

    /**
     * Keyboard visibility from the window stream: an input-method window
     * present means the keyboard is up (ENC-125). Reads window types
     * only — the IME window's content is never touched. On a transition
     * the window-type census that produced the reading is logged with it
     * (types only, no content), so a host app whose window arrangement
     * makes this derivation flap (ENC-127) is diagnosable from logcat.
     * [fromWatchdog] marks readings produced by the ENC-128 watchdog
     * rather than an event, so logcat shows which path converged.
     */
    private fun publishKeyboardVisibility(fromWatchdog: Boolean = false) {
        val currentWindows = windows
        // ENC-132: the shade freeze covers watchdog polls too — a poll
        // that lands while the shade is up holds the last keyboard
        // reading instead of deriving one from the shade's window
        // list. The watchdog's own arm/stop bookkeeping is untouched:
        // the tick that called this still evaluates its stop reason
        // against the held signals and reschedules per ENC-128.
        if (updateShadePresence(currentWindows)) return
        val visible = currentWindows.any { window -> window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        keyboardVisibilityPort.publish(visible)
        if (visible != lastKeyboardVisible) {
            lastKeyboardVisible = visible
            val origin = if (fromWatchdog) " via watchdog poll $watchdogPolls" else ""
            Log.d(TAG, "keyboard visible -> $visible$origin (window types: ${currentWindows.typeCensus()})")
        }
        refreshKeyboardWatchdog()
    }

    /**
     * Arm or disarm the ENC-128 watchdog from the latest signals.
     * Called after every signal publication; arming is idempotent and
     * disarming removes any pending poll.
     */
    private fun refreshKeyboardWatchdog() {
        val armedAt = watchdogArmedAtMs
        if (armedAt == null) {
            if (KeyboardWatchdogPolicy.isStarved(lastPublishedState, lastKeyboardVisible)) {
                watchdogArmedAtMs = SystemClock.elapsedRealtime()
                watchdogPolls = 0
                Log.d(
                    TAG,
                    "keyboard watchdog armed: editable focus held, keyboard reads hidden " +
                        "(window types: ${windows.typeCensus()})",
                )
                watchdogHandler.postDelayed(watchdogTick, KeyboardWatchdogPolicy.POLL_INTERVAL_MS)
            }
        } else {
            val reason = KeyboardWatchdogPolicy.stopReason(
                focusState = lastPublishedState,
                keyboardVisible = lastKeyboardVisible,
                armedForMs = SystemClock.elapsedRealtime() - armedAt,
            )
            if (reason != null) disarmKeyboardWatchdog(reason)
        }
    }

    /**
     * One watchdog poll: re-derive the keyboard signal from the live
     * window list (the IME window may have joined silently since the
     * last event), then keep polling or stop per [KeyboardWatchdogPolicy].
     */
    private fun onWatchdogTick() {
        val armedAt = watchdogArmedAtMs ?: return
        watchdogPolls += 1
        publishKeyboardVisibility(fromWatchdog = true)
        // The publication above refreshes the watchdog; convergence
        // there already disarmed it — nothing left to schedule.
        if (watchdogArmedAtMs == null) return
        val reason = KeyboardWatchdogPolicy.stopReason(
            focusState = lastPublishedState,
            keyboardVisible = lastKeyboardVisible,
            armedForMs = SystemClock.elapsedRealtime() - armedAt,
        )
        if (reason == null) {
            watchdogHandler.postDelayed(watchdogTick, KeyboardWatchdogPolicy.POLL_INTERVAL_MS)
        } else {
            disarmKeyboardWatchdog(reason)
        }
    }

    private fun disarmKeyboardWatchdog(reason: KeyboardWatchdogPolicy.StopReason) {
        watchdogHandler.removeCallbacks(watchdogTick)
        watchdogArmedAtMs = null
        // ENC-145: the census rides the stop line — an EXPIRED arm on an
        // unfolded display shows exactly which windows the derivation saw
        // (an IME that never joins, or joins under another type).
        Log.d(
            TAG,
            "keyboard watchdog stopped: $reason after $watchdogPolls polls " +
                "(window types: ${windows.typeCensus()})",
        )
    }

    /** Stop polling without a policy reason (service unbinding/destroying). */
    private fun stopKeyboardWatchdog() {
        watchdogHandler.removeCallbacks(watchdogTick)
        watchdogArmedAtMs = null
    }

    /**
     * Recompute shade presence from [currentWindows] per
     * [NotificationShadeFreezePolicy] and log transitions with the
     * window-type census (types only, never content), so a shade
     * repro is visible in logcat. [event] — when this recomputation
     * runs for an accessibility event — also engages the freeze via
     * the event's own SystemUI origin (ENC-156): the shade's
     * window-state transition posts before the census reports it.
     * Returns the current presence.
     */
    private fun updateShadePresence(
        currentWindows: List<AccessibilityWindowInfo>,
        event: AccessibilityEvent? = null,
    ): Boolean {
        val byEvent = event != null && NotificationShadeFreezePolicy.isShadeEvent(
            packageName = event.packageName?.toString(),
            isWindowStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        val present = byEvent || NotificationShadeFreezePolicy.isShadePresent(
            currentWindows.map { window -> window.toSnapshot() },
        )
        if (present != shadePresent) {
            shadePresent = present
            val origin = if (byEvent) "via SystemUI window-state event" else "via window census"
            Log.d(TAG, "notification shade present -> $present ($origin; window types: ${currentWindows.typeCensus()})")
        }
        return present
    }

    /** Counts by window type, e.g. "APPLICATION x2, INPUT_METHOD x1" — types only, never content. */
    private fun List<AccessibilityWindowInfo>.typeCensus(): String =
        groupingBy { window -> window.typeName() }
            .eachCount()
            .entries
            .joinToString(separator = ", ") { (type, count) -> "$type x$count" }
            .ifEmpty { "none" }

    /** Type + active flag only — the shade policy never sees a window's content, title, or package. */
    private fun AccessibilityWindowInfo.toSnapshot(): WatcherWindowSnapshot =
        WatcherWindowSnapshot(
            kind = when (type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> WatcherWindowSnapshot.Kind.APPLICATION
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WatcherWindowSnapshot.Kind.INPUT_METHOD
                AccessibilityWindowInfo.TYPE_SYSTEM -> WatcherWindowSnapshot.Kind.SYSTEM
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WatcherWindowSnapshot.Kind.ACCESSIBILITY_OVERLAY
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> WatcherWindowSnapshot.Kind.SPLIT_SCREEN_DIVIDER
                else -> WatcherWindowSnapshot.Kind.OTHER
            },
            isActive = isActive,
        )

    private fun AccessibilityWindowInfo.typeName(): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
        else -> "TYPE_$type"
    }

    private fun focusedNodeSnapshot(event: AccessibilityEvent): FocusedNodeSnapshot? {
        val root = rootInActiveWindow ?: return null
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
            try {
                return focused.toSnapshot(fallbackPackageName = event.packageName?.toString())
            } finally {
                focused.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun AccessibilityNodeInfo.toSnapshot(fallbackPackageName: String?): FocusedNodeSnapshot =
        FocusedNodeSnapshot(
            packageName = packageName?.toString() ?: fallbackPackageName.orEmpty(),
            isEditable = isEditable,
            isPassword = isPassword,
            className = className?.toString(),
        )

    private fun FieldFocusState.describeForLog(): String = when (this) {
        FieldFocusState.NoEditableFocus -> "NoEditableFocus"
        is FieldFocusState.EditableFocus -> "EditableFocus(package=$packageName)"
        is FieldFocusState.PasswordFocus -> "PasswordFocus(package=$packageName)"
    }

    private companion object {
        const val TAG = "FieldWatcher"
        const val EDIT_TEXT_MARKER = "EditText"

        val RELEVANT_EVENT_TYPES: Set<Int> = setOf(
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            // ENC-127: in-field interactions re-derive both signals, so a
            // tap or caret move inside the already-focused field heals a
            // keyboard reading left stale by window churn.
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
        )
    }
}
