package com.shippopotamus.bloviate.service

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.testing.FakeFocusedFieldOperator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Clipboard choreography of paste-first insertion (ARCHITECTURE_RULES
 * §5.3), against Robolectric's real in-memory clipboard: the transcript
 * must be on the clipboard when the paste fires — sensitive-flagged from
 * the moment it is set. Restore-on-deliver puts the user's previous clip
 * back afterwards; leave-on-undeliverable keeps the sensitive-flagged
 * transcript in place so the user can retrieve it; password outcomes always
 * restore, never leaving the transcript behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], application = android.app.Application::class)
class WatcherTextInsertionTest {

    private lateinit var context: Context
    private lateinit var clipboard: ClipboardManager
    private lateinit var bridge: FieldWatcherBridge
    private lateinit var operator: FakeFocusedFieldOperator
    private lateinit var insertion: WatcherTextInsertion

    @BeforeEach
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        clipboard = context.getSystemService(ClipboardManager::class.java)
        bridge = FieldWatcherBridge()
        operator = FakeFocusedFieldOperator(clipboard)
        bridge.register(operator)
        insertion = WatcherTextInsertion(
            bridge = bridge,
            context = context,
            mainDispatcher = UnconfinedTestDispatcher(),
            restoreDelayMs = 0L,
        )
    }

    @Test
    fun `paste happy path - transcript is on the clipboard when paste fires, previous clip restored after`() =
        runBlocking {
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertEquals(
                InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
                result,
            )
            assertEquals(1, operator.pasteCallCount)
            assertEquals(0, operator.spliceCallCount)
            assertEquals("dictated text", operator.clipTextSeenAtPaste)
            assertEquals(true, operator.clipSensitiveSeenAtPaste)
            assertEquals("the user's own words", currentClipboardText())
        }

    @Test
    fun `no watcher connected refuses and never touches the clipboard`() = runBlocking {
        bridge.unregister(operator)
        setClipboardText("keep me")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.WATCHER_UNAVAILABLE),
            result,
        )
        assertEquals(0, operator.pasteCallCount)
        assertEquals("keep me", currentClipboardText())
    }

    @Test
    fun `a password field refuses and the clipboard is restored`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.PASSWORD_FIELD
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD),
            result,
        )
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `no editable field refuses and the sensitive transcript is left on the clipboard`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.NO_EDITABLE_FIELD
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.NO_EDITABLE_FIELD),
            result,
        )
        assertTranscriptLeftOnClipboard("dictated text")
    }

    @Test
    fun `a failed paste reports failure and the sensitive transcript is left on the clipboard`() =
        runBlocking {
            operator.pasteOutcome = FocusedFieldOutcome.FAILED
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
            assertTranscriptLeftOnClipboard("dictated text")
        }

    @Test
    fun `paste unsupported falls back to the splice and restores the clipboard`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.SET_TEXT_SPLICE),
            result,
        )
        assertEquals(listOf("dictated text"), operator.splicedTexts)
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `paste unsupported and the splice meets a password field refuses`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
        operator.spliceOutcome = FocusedFieldOutcome.PASSWORD_FIELD
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD),
            result,
        )
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `splice meeting no editable field refuses and leaves the sensitive transcript`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
        operator.spliceOutcome = FocusedFieldOutcome.NO_EDITABLE_FIELD
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.NO_EDITABLE_FIELD),
            result,
        )
        assertTranscriptLeftOnClipboard("dictated text")
    }

    @Test
    fun `a failed splice reports failure and leaves the sensitive transcript`() = runBlocking {
        operator.pasteOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
        operator.spliceOutcome = FocusedFieldOutcome.FAILED
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
        assertTranscriptLeftOnClipboard("dictated text")
    }

    @Test
    fun `a paste that throws still restores the clipboard and reports failure`() = runBlocking {
        operator.pasteFailure = RuntimeException("framework exploded")
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `an absent previous clipboard is restored to empty, not left holding the transcript`() = runBlocking {
        clipboard.clearPrimaryClip()

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
        assertEquals("", currentClipboardText())
    }

    @Test
    fun `a non-text clipboard item is restored verbatim`() = runBlocking {
        val uriClip = ClipData.newUri(
            context.contentResolver,
            "label",
            Uri.parse("content://example/items/1"),
        )
        clipboard.setPrimaryClip(uriClip)

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
        assertSame(uriClip, clipboard.primaryClip)
    }

    private fun setClipboardText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("label", text))
    }

    private fun currentClipboardText(): String? =
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    private fun assertTranscriptLeftOnClipboard(transcript: String) {
        assertEquals(transcript, currentClipboardText())
        assertTrue(
            clipboard.primaryClip?.description?.extras?.getBoolean(
                ClipDescription.EXTRA_IS_SENSITIVE,
            ) == true,
            "expected the transcript clip to carry EXTRA_IS_SENSITIVE",
        )
    }
}
