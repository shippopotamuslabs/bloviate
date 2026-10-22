package com.shippopotamus.bloviate.domain.evaluator

import javax.inject.Inject

/**
 * Pure splice math for the `ACTION_SET_TEXT` insertion fallback
 * (`contracts/ARCHITECTURE_RULES.md` §5.3): builds the field text with the
 * dictation inserted at the cursor/selection, and places the caret right
 * after the inserted text. An invalid selection (host reporting -1 or an
 * out-of-range range) degrades to appending at the end of the text rather
 * than throwing — the fallback must never lose the user's existing text.
 *
 * Empty hosts surface their placeholder through the same text channel
 * (ENC-168): for an empty `EditText`,
 * `AccessibilityNodeInfo.getText()` returns the hint, so splicing the
 * reported text verbatim would bake the placeholder into the field via
 * `ACTION_SET_TEXT`. When the field is showing its hint — either the
 * explicit [isShowingHintText] flag, or [currentText] exactly equal to a
 * non-empty [hintText] for hosts that do not set the flag — the effective
 * current text is empty and the effective selection is 0..0. Real text
 * that merely differs from the hint splices normally; a null or empty
 * hint changes nothing.
 */
class TextSplicer @Inject constructor() {

    /** Splices [insertion] into [currentText] at the given selection, clamped to valid bounds. */
    fun splice(
        currentText: String,
        selectionStart: Int,
        selectionEnd: Int,
        insertion: String,
        hintText: String? = null,
        isShowingHintText: Boolean = false,
    ): SpliceResult {
        val showingHint = isShowingHintText || (!hintText.isNullOrEmpty() && currentText == hintText)
        val effectiveText = if (showingHint) "" else currentText
        val effectiveStart = if (showingHint) 0 else selectionStart
        val effectiveEnd = if (showingHint) 0 else selectionEnd
        val valid =
            effectiveStart in 0..effectiveText.length &&
                effectiveEnd in effectiveStart..effectiveText.length
        val start = if (valid) effectiveStart else effectiveText.length
        val end = if (valid) effectiveEnd else effectiveText.length
        val text = effectiveText.substring(0, start) + insertion + effectiveText.substring(end)
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
