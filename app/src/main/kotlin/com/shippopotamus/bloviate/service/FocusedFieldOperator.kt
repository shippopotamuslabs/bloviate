package com.shippopotamus.bloviate.service

/**
 * The watcher-side node operations behind paste-first insertion
 * (`contracts/ARCHITECTURE_RULES.md` §5.3), implemented by
 * [FieldWatcherService] — the only component holding an accessibility
 * connection to other apps' windows.
 *
 * Every call re-finds the focused node at call time (nodes are never held
 * across events) and validates it at that moment: password fields refuse,
 * non-editable focus refuses, and every obtained node is recycled before
 * the call returns.
 */
interface FocusedFieldOperator {

    /**
     * Performs `ACTION_PASTE` on the currently focused editable node. The
     * caller must already have placed the text on the clipboard.
     */
    fun pasteIntoFocusedField(): FocusedFieldOutcome

    /**
     * Fallback when paste is unavailable: splices [insertion] into the
     * focused field's text at its cursor/selection via `ACTION_SET_TEXT`.
     */
    fun spliceIntoFocusedField(insertion: String): FocusedFieldOutcome
}

/** Outcome of one [FocusedFieldOperator] node operation. */
enum class FocusedFieldOutcome {

    /** The node action was performed. */
    COMPLETED,

    /** The node does not support the requested action (paste only) — try the fallback. */
    ACTION_UNSUPPORTED,

    /** No editable field holds input focus right now. */
    NO_EDITABLE_FIELD,

    /** The focused field is a password field — never insert, ever. */
    PASSWORD_FIELD,

    /** The action was attempted and the framework reported failure. */
    FAILED,
}
