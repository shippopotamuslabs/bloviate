package com.shippopotamus.bloviate.domain.model

/**
 * Framework-free snapshot of the node returned by `findFocus(FOCUS_INPUT)`.
 *
 * `AccessibilityNodeInfo` cannot be faked in unit tests and must be recycled
 * immediately, so the service layer copies the handful of flags the evaluator
 * needs into this value and recycles the node. Only structural flags and the
 * host package name are captured — never the field's text or content
 * description.
 */
data class FocusedNodeSnapshot(
    val packageName: String,
    val isEditable: Boolean,
    val isPassword: Boolean,
    val className: String?,
)
