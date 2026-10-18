package com.shippopotamus.bloviate.service

import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.testing.FakeRecordingSessionPort
import com.shippopotamus.bloviate.testing.FakeTextPostProcessor
import com.shippopotamus.bloviate.testing.FakeTranscriptSinkPort
import com.shippopotamus.bloviate.testing.FakeTranscriptionEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BubbleRecordingControllerTest {

    private val fakeSession = FakeRecordingSessionPort()
    private val fakeEngine = FakeTranscriptionEngine()
    private val fakePostProcessor = FakeTextPostProcessor()
    private val fakeSink = FakeTranscriptSinkPort()

    private fun createController(scope: CoroutineScope): BubbleRecordingController =
        BubbleRecordingController(
            session = fakeSession,
            transcriptionEngine = fakeEngine,
            textPostProcessor = fakePostProcessor,
            transcriptSink = fakeSink,
            scope = scope,
            workDispatcher = UnconfinedTestDispatcher(),
            doneDisplayMs = DONE_DISPLAY_MS,
            errorDisplayMs = ERROR_DISPLAY_MS,
        )

    @Test
    fun `tap when the session is idle starts it and the bar follows into recording`() = runTest {
        val controller = createController(backgroundScope)
        runCurrent()

        controller.onBubbleTap()
        assertEquals(1, fakeSession.startCallCount)

        fakeSession.setRecording(elapsedMs = 1_000L, amplitude = 0.5f)
        runCurrent()

        assertEquals(RecordingBarState.Recording(elapsedMs = 1_000L), controller.barState.value)
        // The history stores display levels: the raw amplitude is sqrt-scaled
        // and enveloped (see WaveformHistory). 0.5 raw RMS is loud — it maps
        // to a full-scale target, and one attack frame (0.7) lands at 0.7.
        assertEquals(listOf(0.7f), controller.waveform.levels())
    }

    @Test
    fun `recording elapsed updates flow through to the bar`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        fakeSession.setRecording(elapsedMs = 2_500L, amplitude = 0.4f)
        runCurrent()

        assertEquals(RecordingBarState.Recording(elapsedMs = 2_500L), controller.barState.value)
    }

    @Test
    fun `tap when the session is unavailable flashes error then returns to idle`() = runTest {
        val controller = createController(backgroundScope)
        fakeSession.setState(RecordingSessionState.Unavailable)
        runCurrent()

        controller.onBubbleTap()

        assertEquals(0, fakeSession.startCallCount)
        assertEquals(RecordingBarState.Error, controller.barState.value)

        advanceTimeBy(ERROR_DISPLAY_MS)
        runCurrent()
        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `tap while already recording does not start a second session`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        controller.onBubbleTap()

        assertEquals(1, fakeSession.startCallCount)
    }

    @Test
    fun `cancel during recording cancels the session and returns to idle`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        controller.onCancelTap()

        assertEquals(1, fakeSession.cancelCallCount)
        assertEquals(RecordingBarState.Idle, controller.barState.value)
        assertEquals(0, fakeEngine.transcribeCallCount)
        assertTrue(fakeSink.transcripts.isEmpty())
    }

    @Test
    fun `cancel outside recording is ignored`() = runTest {
        val controller = createController(backgroundScope)
        runCurrent()

        controller.onCancelTap()

        assertEquals(0, fakeSession.cancelCallCount)
    }

    @Test
    fun `send during recording stops transcribes delivers and shows done then idle`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "hello world"

        controller.onSendTap()
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)

        runCurrent()

        assertEquals(1, fakeSession.stopCallCount)
        assertEquals(1, fakeEngine.transcribeCallCount)
        assertTrue(fakeEngine.transcribedSamples?.contentEquals(fakeSession.stopResult) == true)
        assertEquals(listOf("hello world"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done, controller.barState.value)

        advanceTimeBy(DONE_DISPLAY_MS)
        runCurrent()
        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `send with a failing engine shows error and delivers nothing`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.failure = IllegalStateException("no model")

        controller.onSendTap()
        runCurrent()

        assertEquals(RecordingBarState.Error, controller.barState.value)
        assertTrue(fakeSink.transcripts.isEmpty())

        advanceTimeBy(ERROR_DISPLAY_MS)
        runCurrent()
        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `send with a failing stop shows error and never transcribes`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSession.stopFailure = IllegalStateException("nothing active")

        controller.onSendTap()
        runCurrent()

        assertEquals(RecordingBarState.Error, controller.barState.value)
        assertEquals(0, fakeEngine.transcribeCallCount)
        assertTrue(fakeSink.transcripts.isEmpty())
    }

    @Test
    fun `send outside recording is ignored`() = runTest {
        val controller = createController(backgroundScope)
        runCurrent()

        controller.onSendTap()
        runCurrent()

        assertEquals(0, fakeSession.stopCallCount)
        assertEquals(0, fakeEngine.transcribeCallCount)
    }

    @Test
    fun `an auto stopped recording takes the send path`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "capped words"
        val cappedSamples = floatArrayOf(0.9f, 0.8f)

        fakeSession.emitAutoStopped(cappedSamples)
        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertTrue(fakeEngine.transcribedSamples?.contentEquals(cappedSamples) == true)
        assertEquals(listOf("capped words"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done, controller.barState.value)
    }

    @Test
    fun `an auto stopped emission while idle is ignored`() = runTest {
        val controller = createController(backgroundScope)
        runCurrent()

        fakeSession.emitAutoStopped()
        runCurrent()

        assertEquals(0, fakeEngine.transcribeCallCount)
        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `host hidden during recording cancels and never transcribes`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        controller.onHostHidden()

        assertEquals(1, fakeSession.cancelCallCount)
        assertEquals(RecordingBarState.Idle, controller.barState.value)

        runCurrent()
        assertEquals(0, fakeEngine.transcribeCallCount)
        assertTrue(fakeSink.transcripts.isEmpty())
    }

    @Test
    fun `host hidden during transcribing discards the transcript`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        val gate = CompletableDeferred<Unit>()
        fakeEngine.gate = gate

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)
        assertEquals(1, fakeEngine.transcribeCallCount)

        controller.onHostHidden()
        runCurrent()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
        assertTrue(fakeSink.transcripts.isEmpty())
        gate.complete(Unit)
    }

    @Test
    fun `the session ending externally while recording returns the bar to idle`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `waveform levels accumulate while recording and clear on cancel`() = runTest {
        val controller = createController(backgroundScope)
        controller.onBubbleTap()
        // Realistic mic RMS: quiet speech, then louder speech.
        fakeSession.setRecording(elapsedMs = 100L, amplitude = 0.04f)
        runCurrent()
        fakeSession.setRecording(elapsedMs = 200L, amplitude = 0.1f)
        runCurrent()

        val levels = controller.waveform.levels()
        assertEquals(2, levels.size)
        assertTrue(levels[0] > 0.1f, "gained level should clear the stub range: $levels")
        assertTrue(levels[1] > levels[0], "louder speech should raise the bar: $levels")

        controller.onCancelTap()
        assertTrue(controller.waveform.levels().isEmpty())
    }

    @Test
    fun `a reported transient elapse returns done to idle without waiting for the timer`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "hello world"
        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Done, controller.barState.value)

        // The composition's backstop (ENC-65): no virtual time passes, yet
        // the bubble must not strand on the check.
        controller.onTransientStateElapsed()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `a reported transient elapse returns error to idle`() = runTest {
        val controller = createController(backgroundScope)
        fakeSession.setState(RecordingSessionState.Unavailable)
        runCurrent()
        controller.onBubbleTap()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        controller.onTransientStateElapsed()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `a reported transient elapse is a no-op outside the transient states`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        controller.onTransientStateElapsed()

        assertEquals(
            RecordingBarState.Recording(elapsedMs = 500L),
            controller.barState.value,
        )
        assertEquals(0, fakeSession.cancelCallCount)
    }

    @Test
    fun `send delivers the post-processed text, not the raw transcript`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "um hello world"
        fakePostProcessor.transform = { "Hello world" }

        controller.onSendTap()
        runCurrent()

        assertEquals(listOf("um hello world"), fakePostProcessor.inputs)
        assertEquals(listOf("Hello world"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done, controller.barState.value)
    }

    @Test
    fun `send whose transcript cleans to blank delivers nothing and shows done`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "um uh"
        fakePostProcessor.transform = { "" }

        controller.onSendTap()
        runCurrent()

        assertTrue(fakeSink.transcripts.isEmpty())
        assertEquals(RecordingBarState.Done, controller.barState.value)
    }

    @Test
    fun `send the sink refuses shows error, not done`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult =
            InsertionResult.Refused(InsertionResult.Refused.Reason.PASSWORD_FIELD)

        controller.onSendTap()
        runCurrent()

        assertEquals(listOf("hello world"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Error, controller.barState.value)
    }

    @Test
    fun `send the sink fails shows error, not done`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("clipboard gone")

        controller.onSendTap()
        runCurrent()

        assertEquals(RecordingBarState.Error, controller.barState.value)
    }

    @Test
    fun `a failed insertion keeps the samples and a tap retries the send`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.resultQueue.addLast(InsertionResult.Failed("paste exploded"))
        fakeSink.resultQueue.addLast(
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE),
        )

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)
        assertEquals(1, fakeEngine.transcribeCallCount)

        controller.onBubbleTap()
        runCurrent()

        assertEquals(2, fakeEngine.transcribeCallCount)
        assertEquals(2, fakeSink.transcripts.size)
        assertEquals(RecordingBarState.Done, controller.barState.value)
    }

    @Test
    fun `a failed transcription keeps the samples and a tap retries the send`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.failure = IllegalStateException("engine cold")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        fakeEngine.failure = null
        controller.onBubbleTap()
        runCurrent()

        assertEquals(2, fakeEngine.transcribeCallCount)
        assertEquals(listOf("hello world"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done, controller.barState.value)
    }

    @Test
    fun `the retry is spent after one attempt`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("always fails")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        controller.onBubbleTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)
        assertEquals(2, fakeEngine.transcribeCallCount)

        // Second failure spent the retry: another tap in the error window
        // must not run the pipeline a third time.
        controller.onBubbleTap()
        runCurrent()
        assertEquals(2, fakeEngine.transcribeCallCount)
    }

    @Test
    fun `the retained retry expires when the error window elapses`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("paste exploded")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        advanceTimeBy(ERROR_DISPLAY_MS)
        runCurrent()
        assertEquals(RecordingBarState.Idle, controller.barState.value)

        // A tap now is a fresh recording, not a retry of the dead one.
        fakeSession.setState(RecordingSessionState.Idle)
        controller.onBubbleTap()
        runCurrent()
        assertEquals(2, fakeSession.startCallCount)
        assertEquals(1, fakeEngine.transcribeCallCount)
    }

    @Test
    fun `host hidden during the error window drops the retained retry`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("paste exploded")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        controller.onHostHidden()
        assertEquals(RecordingBarState.Idle, controller.barState.value)

        // A tap now starts a new recording; the retained samples are gone.
        fakeSession.setState(RecordingSessionState.Idle)
        controller.onBubbleTap()
        runCurrent()
        assertEquals(2, fakeSession.startCallCount)
        assertEquals(1, fakeEngine.transcribeCallCount)
    }

    @Test
    fun `low memory clears the waveform history`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        assertTrue(controller.waveform.levels().isNotEmpty())

        controller.onLowMemory()

        assertTrue(controller.waveform.levels().isEmpty())
    }

    /** Drives the controller into an active recording with one amplitude recorded. */
    private fun TestScope.startRecording(controller: BubbleRecordingController) {
        controller.onBubbleTap()
        fakeSession.setRecording(elapsedMs = 500L, amplitude = 0.5f)
        runCurrent()
        assertEquals(RecordingBarState.Recording(elapsedMs = 500L), controller.barState.value)
    }

    private companion object {
        const val DONE_DISPLAY_MS: Long = 100L
        const val ERROR_DISPLAY_MS: Long = 200L
    }
}
