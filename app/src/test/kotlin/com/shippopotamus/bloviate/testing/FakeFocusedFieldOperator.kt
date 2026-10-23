package com.shippopotamus.bloviate.testing

import android.content.ClipDescription
import android.content.ClipboardManager
import com.shippopotamus.bloviate.service.FocusedFieldOperator
import com.shippopotamus.bloviate.service.FocusedFieldOutcome

/**
 * Hand-written fake of the watcher's node operator. Outcomes are scripted;
 * [pasteIntoFocusedField] also reads the clipboard at the moment the paste
 * fires, so tests can prove the transcript was in place by then
 * (snapshot → set → paste → restore ordering).
 */
class FakeFocusedFieldOperator(
    private val clipboard: ClipboardManager,
) : FocusedFieldOperator {

    var pasteOutcome: FocusedFieldOutcome = FocusedFieldOutcome.COMPLETED
    var spliceOutcome: FocusedFieldOutcome = FocusedFieldOutcome.COMPLETED
    var pasteFailure: Exception? = null

    var pasteCallCount: Int = 0
        private set
    var spliceCallCount: Int = 0
        private set
    var clipTextSeenAtPaste: String? = null
        private set
    var clipSensitiveSeenAtPaste: Boolean? = null
        private set
    val splicedTexts: MutableList<String> = mutableListOf()

    override fun pasteIntoFocusedField(): FocusedFieldOutcome {
        pasteCallCount++
        pasteFailure?.let { throw it }
        clipTextSeenAtPaste =
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        clipSensitiveSeenAtPaste =
            clipboard.primaryClip?.description?.extras?.getBoolean(
                ClipDescription.EXTRA_IS_SENSITIVE,
            )
        return pasteOutcome
    }

    override fun spliceIntoFocusedField(insertion: String): FocusedFieldOutcome {
        spliceCallCount++
        splicedTexts += insertion
        return spliceOutcome
    }
}
