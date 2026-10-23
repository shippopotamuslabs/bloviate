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
 * Clipboard choreography of splice-first insertion (ENC-168): the
 * watcher's `ACTION_SET_TEXT` splice is tried first without touching the
 * clipboard at all. Only when the splice cannot deliver does the paste
 * fallback snapshot the clipboard, set the sensitive-flagged transcript,
 * and paste. Restore-on-deliver puts the user's previous clip back
 * afterwards; leave-on-undeliverable keeps the sensitive-flagged
 * transcript in place so the user can retrieve it; password outcomes
 * never leave the transcript behind.
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
    fun `splice happy path - clipboard never touched, previous clip kept`() = runBlocking {
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.SET_TEXT_SPLICE),
            result,
        )
        assertEquals(listOf("dictated text"), operator.splicedTexts)
        assertEquals(0, operator.pasteCallCount)
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `splice happy path - a non-text clipboard item is identical, proving no rewrite`() =
        runBlocking {
            val uriClip = ClipData.newUri(
                context.contentResolver,
                "label",
                Uri.parse("content://example/items/1"),
            )
            clipboard.setPrimaryClip(uriClip)

            val result = insertion.insert("dictated text")

            assertEquals(
                InsertionResult.Inserted(InsertionResult.Inserted.Method.SET_TEXT_SPLICE),
                result,
            )
            assertEquals(0, operator.pasteCallCount)
            assertSame(uriClip, clipboard.primaryClip)
        }

    @Test
    fun `splice happy path with no previous clipboard leaves no transcript behind`() = runBlocking {
        clipboard.clearPrimaryClip()

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.SET_TEXT_SPLICE),
            result,
        )
        assertEquals(0, operator.pasteCallCount)
        val current = currentClipboardText()
        assertTrue(
            current == null || current.isEmpty(),
            "expected no transcript on the clipboard, was <$current>",
        )
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
        assertEquals(0, operator.spliceCallCount)
        assertEquals("keep me", currentClipboardText())
    }

    @Test
    fun `a password field refuses without paste and the clipboard is untouched`() = runBlocking {
        operator.spliceOutcome = FocusedFieldOutcome.PASSWORD_FIELD
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD),
            result,
        )
        assertEquals(0, operator.pasteCallCount)
        assertEquals("the user's own words", currentClipboardText())
        assertTrue(
            currentClipboardText() != "dictated text",
            "transcript must never linger after a password refusal",
        )
    }

    @Test
    fun `no editable field refuses without paste and the sensitive transcript is left on the clipboard`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.NO_EDITABLE_FIELD
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertEquals(
                InsertionResult.Refused(InsertionResult.Refused.Reason.NO_EDITABLE_FIELD),
                result,
            )
            assertEquals(0, operator.pasteCallCount)
            assertTranscriptLeftOnClipboard("dictated text")
        }

    @Test
    fun `splice unsupported falls back to paste - transcript on clipboard at paste, previous clip restored`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertEquals(
                InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
                result,
            )
            assertEquals(listOf("dictated text"), operator.splicedTexts)
            assertEquals(1, operator.pasteCallCount)
            assertEquals("dictated text", operator.clipTextSeenAtPaste)
            assertEquals(true, operator.clipSensitiveSeenAtPaste)
            assertEquals("the user's own words", currentClipboardText())
        }

    @Test
    fun `splice failed falls back to paste and restores the previous clip`() = runBlocking {
        operator.spliceOutcome = FocusedFieldOutcome.FAILED
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
        assertEquals(1, operator.pasteCallCount)
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `a throwing splice falls back to paste and still delivers`() = runBlocking {
        operator.spliceFailure = RuntimeException("framework exploded")
        setClipboardText("the user's own words")

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
        assertEquals(1, operator.pasteCallCount)
        assertEquals("the user's own words", currentClipboardText())
    }

    @Test
    fun `a failed paste in the fallback reports failure and the sensitive transcript is left`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
            operator.pasteOutcome = FocusedFieldOutcome.FAILED
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
            assertTranscriptLeftOnClipboard("dictated text")
        }

    @Test
    fun `an unsupported paste in the fallback reports failure and the sensitive transcript is left`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.FAILED
            operator.pasteOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
            assertTranscriptLeftOnClipboard("dictated text")
        }

    @Test
    fun `paste losing focus mid-fallback refuses and leaves the sensitive transcript`() = runBlocking {
        operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
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
    fun `paste meeting a password field in the fallback refuses and restores the clipboard`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
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
    fun `a paste that throws in the fallback still restores the clipboard and reports failure`() =
        runBlocking {
            operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
            operator.pasteFailure = RuntimeException("framework exploded")
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
            assertEquals("the user's own words", currentClipboardText())
        }

    @Test
    fun `a throwing splice with a throwing paste restores the clipboard and reports failure`() =
        runBlocking {
            operator.spliceFailure = RuntimeException("splice exploded")
            operator.pasteFailure = RuntimeException("paste exploded")
            setClipboardText("the user's own words")

            val result = insertion.insert("dictated text")

            assertTrue(result is InsertionResult.Failed, "expected Failed, was $result")
            assertEquals("the user's own words", currentClipboardText())
        }

    @Test
    fun `an absent previous clipboard in the paste fallback is restored to empty`() = runBlocking {
        operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
        clipboard.clearPrimaryClip()

        val result = insertion.insert("dictated text")

        assertEquals(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
            result,
        )
        assertEquals("", currentClipboardText())
    }

    @Test
    fun `a non-text clipboard item is restored verbatim after a fallback paste`() = runBlocking {
        operator.spliceOutcome = FocusedFieldOutcome.ACTION_UNSUPPORTED
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
