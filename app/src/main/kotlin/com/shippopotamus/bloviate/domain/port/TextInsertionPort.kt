package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.InsertionResult

/**
 * The insertion seam (`contracts/ARCHITECTURE_RULES.md` §5.3): delivers one
 * finished transcript into whatever editable field currently holds input
 * focus, through the accessibility watcher — the only component that can
 * act on another app's focused node.
 *
 * Paste-first contract: the implementation sets the clipboard and performs
 * `ACTION_PASTE` on the focused node, restoring the user's previous
 * clipboard afterwards; an `ACTION_SET_TEXT` splice at the cursor is the
 * fallback only when paste is unavailable. Password fields are always
 * refused, and a failed attempt leaves the field's existing text untouched.
 */
interface TextInsertionPort {

    /** Inserts [text] into the currently focused editable field, if any. */
    suspend fun insert(text: String): InsertionResult
}
