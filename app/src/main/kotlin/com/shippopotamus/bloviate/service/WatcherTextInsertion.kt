package com.shippopotamus.bloviate.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.application.port.TextInsertionPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [TextInsertionPort] via the accessibility watcher, paste-first
 * (`contracts/ARCHITECTURE_RULES.md` §5.3):
 *
 * 1. Snapshot the user's clipboard (best effort — Android restricts
 *    clipboard reads for apps without input focus, in which case the
 *    snapshot is absent and restoration clears the clip instead).
 * 2. Put the transcript on the clipboard — sensitive-flagged from the
 *    moment it is set, so system previews and clipboard history redact it —
 *    and let the watcher perform `ACTION_PASTE` on the focused node.
 * 3. Restore-on-deliver: after a short delay — the host app reads the
 *    clipboard while handling the paste, so restoring must not race it —
 *    put the snapshot back (or an empty clip when there was no snapshot).
 *
 * Leave-on-undeliverable: when the text could not land in the field
 * (paste path `NO_EDITABLE_FIELD` or `FAILED`; splice fallback
 * `NO_EDITABLE_FIELD`, `FAILED`, or `ACTION_UNSUPPORTED` after a splice
 * attempt), the sensitive-flagged transcript clip is left in place so the
 * user can still retrieve it via the clipboard overlay — the clip itself
 * is the deliverable. The restored snapshot clip is always the user's own
 * clip back byte-identical; the transcript clip is never restored over it.
 *
 * Never-leave-on-password: a `PASSWORD_FIELD` outcome restores immediately
 * on every path, never leaving the transcript behind (belt-and-braces).
 * Cancellation and unexpected exceptions also restore. Early returns before
 * any clip is set (watcher/clipboard unavailable) leave the clipboard
 * untouched.
 *
 * Only when the node reports paste unsupported does it fall back to the
 * watcher's `ACTION_SET_TEXT` splice; a completed splice restores the
 * clipboard immediately, since the host never reads it. The field's
 * existing text is never touched by a failed attempt.
 *
 * Transcript text is never logged here.
 */
class WatcherTextInsertion(
    private val bridge: FieldWatcherBridge,
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val restoreDelayMs: Long = CLIPBOARD_RESTORE_DELAY_MS,
) : TextInsertionPort {

    override suspend fun insert(text: String): InsertionResult = withContext(mainDispatcher) {
        val operator = bridge.currentOperator()
            ?: return@withContext InsertionResult.Refused(InsertionResult.Refused.Reason.WATCHER_UNAVAILABLE)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return@withContext InsertionResult.Failed("ClipboardManager unavailable")
        val snapshot = readSnapshot(clipboard)
        try {
            clipboard.setPrimaryClip(sensitiveTranscriptClip(text))
            when (operator.pasteIntoFocusedField()) {
                FocusedFieldOutcome.COMPLETED -> {
                    delay(restoreDelayMs)
                    restoreClipboard(clipboard, snapshot)
                    InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE)
                }
                FocusedFieldOutcome.ACTION_UNSUPPORTED -> spliceFallback(operator, clipboard, snapshot, text)
                FocusedFieldOutcome.PASSWORD_FIELD -> {
                    restoreClipboard(clipboard, snapshot)
                    InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD)
                }
                FocusedFieldOutcome.NO_EDITABLE_FIELD -> {
                    leaveTranscript("paste found no editable field")
                    InsertionResult.Refused(InsertionResult.Refused.Reason.NO_EDITABLE_FIELD)
                }
                FocusedFieldOutcome.FAILED -> {
                    leaveTranscript("ACTION_PASTE reported failure")
                    InsertionResult.Failed("ACTION_PASTE reported failure")
                }
            }
        } catch (exception: CancellationException) {
            restoreClipboard(clipboard, snapshot)
            throw exception
        } catch (exception: Exception) {
            restoreClipboard(clipboard, snapshot)
            InsertionResult.Failed("Insertion threw ${exception.javaClass.simpleName}")
        }
    }

    /**
     * The paste-unsupported path: `ACTION_SET_TEXT` splice via the watcher.
     * A completed splice restores the clipboard at once, since the host
     * never reads it; a password outcome restores too, never leaving the
     * transcript behind. Any other outcome leaves the sensitive-flagged
     * transcript clip in place — the field vanished or failed, so the clip
     * itself is the deliverable.
     */
    private fun spliceFallback(
        operator: FocusedFieldOperator,
        clipboard: ClipboardManager,
        snapshot: ClipData?,
        text: String,
    ): InsertionResult {
        val outcome = operator.spliceIntoFocusedField(text)
        return when (outcome) {
            FocusedFieldOutcome.COMPLETED -> {
                restoreClipboard(clipboard, snapshot)
                InsertionResult.Inserted(InsertionResult.Inserted.Method.SET_TEXT_SPLICE)
            }
            FocusedFieldOutcome.PASSWORD_FIELD -> {
                restoreClipboard(clipboard, snapshot)
                InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD)
            }
            FocusedFieldOutcome.NO_EDITABLE_FIELD -> {
                leaveTranscript("splice found no editable field")
                InsertionResult.Refused(InsertionResult.Refused.Reason.NO_EDITABLE_FIELD)
            }
            FocusedFieldOutcome.ACTION_UNSUPPORTED,
            FocusedFieldOutcome.FAILED,
            -> {
                leaveTranscript("ACTION_SET_TEXT splice reported $outcome")
                InsertionResult.Failed("ACTION_SET_TEXT splice reported $outcome")
            }
        }
    }

    /**
     * Builds the transcript clip with [ClipDescription.EXTRA_IS_SENSITIVE]
     * set from the moment it is placed, so system previews and clipboard
     * history redact it. Applies to the transcript clip only — the user's
     * snapshot clip is always restored byte-identical, never re-flagged.
     */
    private fun sensitiveTranscriptClip(text: String): ClipData {
        val clip = ClipData.newPlainText(CLIP_LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        return clip
    }

    /**
     * Best-effort read of the user's current clip. Platforms that deny the
     * read (no input focus) yield null, which restoration treats as
     * "restore to empty" rather than leaving the transcript behind.
     */
    private fun readSnapshot(clipboard: ClipboardManager): ClipData? =
        try {
            clipboard.primaryClip
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read the current clipboard; restore will clear it", exception)
            null
        }

    /**
     * Undeliverable outcome: the sensitive-flagged transcript clip stays on
     * the clipboard so the user can retrieve it via the clipboard overlay.
     * Logs the decision only — never the transcript text.
     */
    private fun leaveTranscript(reason: String) {
        Log.i(TAG, "Transcript undeliverable ($reason); leaving it on the clipboard")
    }

    /** Puts [snapshot] back, or an empty clip when there was nothing to snapshot. Never throws. */
    private fun restoreClipboard(clipboard: ClipboardManager, snapshot: ClipData?) {
        try {
            clipboard.setPrimaryClip(snapshot ?: ClipData.newPlainText(CLIP_LABEL, ""))
        } catch (exception: Exception) {
            Log.w(TAG, "Could not restore the previous clipboard", exception)
        }
    }

    private companion object {
        const val TAG: String = "BubbleOverlay"
        const val CLIP_LABEL: String = "Bloviate dictation"

        /**
         * How long the transcript stays on the clipboard after
         * `ACTION_PASTE` before the user's previous clip is restored: long
         * enough for the host app to read it while handling the paste,
         * short enough to feel instant.
         */
        const val CLIPBOARD_RESTORE_DELAY_MS: Long = 400L
    }
}
