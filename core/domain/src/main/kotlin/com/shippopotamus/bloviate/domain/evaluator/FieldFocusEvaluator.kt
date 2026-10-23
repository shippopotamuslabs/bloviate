package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.model.FocusedNodeSnapshot

/**
 * Pure classification of a focused node snapshot into a [FieldFocusState].
 *
 * Rules (contracts/ARCHITECTURE_RULES.md §5.1):
 * - Password wins over everything: a password field is never a dictation
 *   target, even though it is editable.
 * - A node counts as editable when the framework says so **or** its class
 *   name contains `EditText` — WebView and legacy inputs lie about
 *   `isEditable`, and editable WebView nodes must still classify editable.
 */
object FieldFocusEvaluator {

    fun evaluate(snapshot: FocusedNodeSnapshot?): FieldFocusState = when {
        snapshot == null -> FieldFocusState.NoEditableFocus
        snapshot.isPassword -> FieldFocusState.PasswordFocus(snapshot.packageName)
        snapshot.isEditable || snapshot.className?.contains(EDIT_TEXT_MARKER) == true ->
            FieldFocusState.EditableFocus(snapshot.packageName)
        else -> FieldFocusState.NoEditableFocus
    }

    private const val EDIT_TEXT_MARKER = "EditText"
}
