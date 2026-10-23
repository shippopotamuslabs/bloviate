package com.shippopotamus.bloviate.domain.evaluator

import javax.inject.Inject

/**
 * Pure splice math for the `ACTION_SET_TEXT` insertion fallback
 * (`contracts/ARCHITECTURE_RULES.md` §5.3): builds the field text with the
 * dictation inserted at the cursor/selection, and places the caret right
 * after the inserted text. An invalid selection (host reporting -1 or an
 * out-of-range range) degrades to appending at the end of the text rather
 * than throwing — the fallback must never lose the user's existing text.
 */
class TextSplicer @Inject constructor() {

    /** Splices [insertion] into [currentText] at the given selection, clamped to valid bounds. */
    fun splice(
        currentText: String,
        selectionStart: Int,
        selectionEnd: Int,
        insertion: String,
    ): SpliceResult {
        val valid =
            selectionStart in 0..currentText.length &&
                selectionEnd in selectionStart..currentText.length
        val start = if (valid) selectionStart else currentText.length
        val end = if (valid) selectionEnd else currentText.length
        val text = currentText.substring(0, start) + insertion + currentText.substring(end)
        val caret = start + insertion.length
        return SpliceResult(text = text, selectionStart = caret, selectionEnd = caret)
    }
}

/** The spliced field text and the collapsed caret/selection after the inserted text. */
data class SpliceResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)
