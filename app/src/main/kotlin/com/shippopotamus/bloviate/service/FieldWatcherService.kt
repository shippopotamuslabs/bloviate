package com.shippopotamus.bloviate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.shippopotamus.bloviate.domain.evaluator.FieldFocusEvaluator
import com.shippopotamus.bloviate.domain.evaluator.TextSplicer
import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.FocusedNodeSnapshot
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
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
    lateinit var insertionBridge: FieldWatcherBridge

    @Inject
    lateinit var textSplicer: TextSplicer

    private var lastPublishedState: FieldFocusState = FieldFocusState.NoEditableFocus
    private var lastKeyboardVisible: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        insertionBridge.register(this)
        publishKeyboardVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in RELEVANT_EVENT_TYPES) return
        publish(FieldFocusEvaluator.evaluate(focusedNodeSnapshot(event)))
        publishKeyboardVisibility()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        insertionBridge.unregister(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        insertionBridge.unregister(this)
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
    }

    /**
     * Keyboard visibility from the window stream: an input-method window
     * present means the keyboard is up (ENC-125). Reads window types
     * only — the IME window's content is never touched.
     */
    private fun publishKeyboardVisibility() {
        val visible = windows.any { window -> window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        keyboardVisibilityPort.publish(visible)
        if (visible != lastKeyboardVisible) {
            lastKeyboardVisible = visible
            Log.d(TAG, "keyboard visible -> $visible")
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
        const val EDIT_TEXT_MARKER = "EditText"

        val RELEVANT_EVENT_TYPES: Set<Int> = setOf(
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
    }
}
