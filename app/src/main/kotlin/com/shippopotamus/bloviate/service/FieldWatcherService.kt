package com.shippopotamus.bloviate.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.shippopotamus.bloviate.domain.evaluator.FieldFocusEvaluator
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.FocusedNodeSnapshot
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches the focused input field system-wide and publishes a deduped
 * [FieldFocusState] through [FieldFocusPort] for the bubble overlay (ENC-59).
 *
 * Privacy contract: only the host package name and structural flags are ever
 * read. Field text and content descriptions are never read, logged, or stored,
 * and password fields classify as hidden. Every [AccessibilityNodeInfo]
 * obtained here is recycled before the event returns; no node state survives
 * an event.
 */
@AndroidEntryPoint
class FieldWatcherService : AccessibilityService() {

    @Inject
    lateinit var fieldFocusPort: FieldFocusPort

    private var lastPublishedState: FieldFocusState = FieldFocusState.NoEditableFocus

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in RELEVANT_EVENT_TYPES) return
        publish(FieldFocusEvaluator.evaluate(focusedNodeSnapshot(event)))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        publish(FieldFocusState.NoEditableFocus)
        super.onDestroy()
    }

    private fun publish(state: FieldFocusState) {
        fieldFocusPort.publish(state)
        if (state != lastPublishedState) {
            lastPublishedState = state
            Log.d(TAG, "focus state -> ${state.describeForLog()}")
        }
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

        val RELEVANT_EVENT_TYPES: Set<Int> = setOf(
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
    }
}
