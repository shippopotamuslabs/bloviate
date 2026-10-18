package com.shippopotamus.bloviate.domain.model

/**
 * Classification of the currently focused input field, system-wide.
 *
 * Published by the accessibility watcher and consumed by the bubble overlay
 * (ENC-59). Carries only the host app's package name and the classification —
 * never field content.
 */
sealed interface FieldFocusState {

    /** No editable field currently holds input focus. The bubble stays hidden. */
    data object NoEditableFocus : FieldFocusState

    /** An editable, non-password field in [packageName] holds input focus. */
    data class EditableFocus(val packageName: String) : FieldFocusState

    /** A password field in [packageName] holds input focus. The bubble must stay hidden. */
    data class PasswordFocus(val packageName: String) : FieldFocusState
}
