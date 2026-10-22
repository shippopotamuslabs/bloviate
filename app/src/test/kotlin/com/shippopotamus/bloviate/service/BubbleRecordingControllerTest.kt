package com.shippopotamus.bloviate.service

import com.shippopotamus.bloviate.domain.model.AutoFinishReason
import com.shippopotamus.bloviate.domain.model.InsertionResult
import com.shippopotamus.bloviate.domain.model.RecordingBarState
import com.shippopotamus.bloviate.domain.model.StopResult
import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.domain.evaluator.StylingTextPostProcessor
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeEnginePreferencePort
import com.shippopotamus.bloviate.testing.FakeLlmStylePort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import com.shippopotamus.bloviate.testing.FakeRecordingSessionPort
import com.shippopotamus.bloviate.testing.FakeStylePreferencePort
import com.shippopotamus.bloviate.testing.FakeTextPostProcessor
import com.shippopotamus.bloviate.testing.FakeTranscriptSinkPort
import com.shippopotamus.bloviate.testing.FakeTranscriptionEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BubbleRecordingControllerTest {

    private val fakeSession = FakeRecordingSessionPort()
    private val fakeEngine = FakeTranscriptionEngine()
    private val fakePostProcessor = FakeTextPostProcessor()
    private val fakeSink = FakeTranscriptSinkPort()
    private val fakeEnginePreference = FakeEnginePreferencePort()
    private val fakeWhisperStore = FakeModelStorePort()
    private val fakeParakeetStore = FakeParakeetModelStorePort()
    private val fakeStylePreference = FakeStylePreferencePort()
    private val fakeLlm = FakeLlmStylePort()
    private val fakeApiKeys = FakeApiKeyPort()

    private fun createController(
        scope: CoroutineScope,
        isEngineWarming: () -> Boolean = { false },
    ): BubbleRecordingController =
        BubbleRecordingController(
            session = fakeSession,
            transcriptionEngine = fakeEngine,
            stylingProcessor = StylingTextPostProcessor(
                local = fakePostProcessor,
                stylePreference = fakeStylePreference,
                llm = fakeLlm,
                apiKeys = fakeApiKeys,
            ),
            transcriptSink = fakeSink,
            enginePreference = fakeEnginePreference,
            whisperStore = fakeWhisperStore,
            parakeetStore = fakeParakeetStore,
            scope = scope,
            workDispatcher = UnconfinedTestDispatcher(),
            doneDisplayMs = DONE_DISPLAY_MS,
            errorDisplayMs = ERROR_DISPLAY_MS,
            isEngineWarming = isEngineWarming,
        )

    @Test
    fun `tap when the session is idle starts it and the bar follows into recording`() = runTest {
        val controller = createController(backgroundScope)
        runCurrent()

        controller.onBubbleTap()
        assertEquals(1, fakeSession.startCallCount)

        fakeSession.setRecording(elapsedMs = 1_000L, amplitude = 0.5f)
        runCurrent()

        assertEquals(
            RecordingBarState.Recording(elapsedMs = 1_000L, frame = 1L),
            controller.barState.value,
        )
        // The history stores display levels: the raw amplitude is sqrt-scaled
        // and enveloped (see WaveformHistory). 0.5 raw RMS is loud — it maps
        // to a full-scale target, and one attack frame (0.7) lands at 0.7.
        assertEquals(listOf(0.7f), controller.waveform.levels())
    }

    @Test
    fun `tap while the engine is warming is a clean no-op`() = runTest {
        val controller = createController(backgroundScope, isEngineWarming = { true })
        runCurrent()

        controller.onBubbleTap()

        // No recording started, no error flashed, nothing queued.
        assertEquals(0, fakeSession.startCallCount)
        assertEquals(RecordingBarState.Idle, controller.barState.value)
    }

    @Test
    fun `tap records once the engine finishes warming`() = runTest {
        var warming = true
        val controller = createController(backgroundScope, isEngineWarming = { warming })
        runCurrent()

        controller.onBubbleTap()
        assertEquals(0, fakeSession.startCallCount)

        warming = false
        controller.onBubbleTap()
        assertEquals(1, fakeSession.startCallCount)
    }

    @Test
    fun `recording elapsed updates flow through to the bar`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        fakeSession.setRecording(elapsedMs = 2_500L, amplitude = 0.4f)
        runCurrent()

        assertEquals(
            RecordingBarState.Recording(elapsedMs = 2_500L, frame = 2L),
            controller.barState.value,
        )
    }

    @Test
    fun `a new amplitude at the same elapsed does not republish but still records levels`() = runTest {
        // ENC-162: republishing at the amplitude cadence (~15-30 Hz)
        // recomposed the overlay mid-drag and corrupted pointer deltas
        // (drag jitter). Only a new elapsed tick republishes — the
        // waveform Canvas re-reads the levels at display rate through its
        // deferred lambda, so record() must still run on every update.
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        val publishedBefore = emissions.filterIsInstance<RecordingBarState.Recording>().size
        val levelsBefore = controller.waveform.levels().size

        // Same chunk (elapsed unchanged), a different voice level.
        fakeSession.setRecording(elapsedMs = 500L, amplitude = 0.05f)
        runCurrent()

        val recordings = emissions.filterIsInstance<RecordingBarState.Recording>()
        assertEquals(
            publishedBefore,
            recordings.size,
            "a waveform-only change must not republish the bar state, got $emissions",
        )
        assertEquals(
            RecordingBarState.Recording(elapsedMs = 500L, frame = 1L),
            controller.barState.value,
        )
        assertTrue(
            controller.waveform.levels().size > levelsBefore,
            "the amplitude must still land in the waveform history: " +
                "${controller.waveform.levels()}",
        )
    }

    @Test
    fun `elapsed advancing within the same displayed second does not republish but still records levels`() = runTest {
        // ENC-162 round 3: the session emits per ~33ms amplitude window,
        // so elapsed advances ~30x/second while the m:ss readout is
        // unchanged — republishing those sub-second ticks recomposes the
        // overlay mid-drag and corrupts pointer deltas (drag jitter).
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        val publishedBefore = emissions.filterIsInstance<RecordingBarState.Recording>().size
        val levelsBefore = controller.waveform.levels().size

        // Same displayed second (0), elapsed advanced.
        fakeSession.setRecording(elapsedMs = 700L, amplitude = 0.05f)
        runCurrent()

        val recordings = emissions.filterIsInstance<RecordingBarState.Recording>()
        assertEquals(
            publishedBefore,
            recordings.size,
            "a sub-second elapsed change must not republish the bar state, got $emissions",
        )
        assertEquals(
            RecordingBarState.Recording(elapsedMs = 500L, frame = 1L),
            controller.barState.value,
        )
        assertTrue(
            controller.waveform.levels().size > levelsBefore,
            "the amplitude must still land in the waveform history: " +
                "${controller.waveform.levels()}",
        )
    }

    @Test
    fun `crossing a displayed-second boundary republishes with an advanced frame`() = runTest {
        // ENC-162 companion: only the displayed second (elapsedMs / 1000)
        // is the republish trigger, and successive publishes stay
        // distinguishable via the frame tick.
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        val first = emissions.filterIsInstance<RecordingBarState.Recording>().last()

        fakeSession.setRecording(elapsedMs = 1_500L, amplitude = 0.05f)
        runCurrent()

        val recordings = emissions.filterIsInstance<RecordingBarState.Recording>()
        val last = recordings.last()
        assertTrue(recordings.size >= 2, "the second boundary must republish, got $emissions")
        assertEquals(1_500L, last.elapsedMs)
        assertNotEquals(first, last)
        assertTrue(last.frame > first.frame, "the tick must advance: $recordings")
        assertEquals(last, controller.barState.value)
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
        assertTrue(fakeEngine.transcribedSamples?.contentEquals(fakeSession.stopResult.samples) == true)
        assertEquals(listOf("hello world"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(), controller.barState.value)

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
        fakeSession.stopResult = StopResult(cappedSamples, AutoFinishReason.MAX_LENGTH)

        fakeSession.emitAutoStopped(cappedSamples)
        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertTrue(fakeEngine.transcribedSamples?.contentEquals(cappedSamples) == true)
        assertEquals(listOf("capped words"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH), controller.barState.value)
    }

    @Test
    fun `an auto-finished recording whose session idle lands first still transcribes exactly once`() = runTest {
        // ENC-155: the session-first ordering, forced deterministically —
        // the gate holds the session-side send in Transcribing while the
        // emission lands, so the emission is ignored as the same
        // recording, never re-sent. One transcription, and Done names the
        // reason the stop result carried.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "capped words"
        fakeSession.stopResult =
            StopResult(floatArrayOf(0.4f, 0.5f), AutoFinishReason.MAX_LENGTH)
        val gate = CompletableDeferred<Unit>()
        fakeEngine.gate = gate

        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)
        assertEquals(1, fakeEngine.transcribeCallCount)

        fakeSession.emitAutoStopped()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("capped words"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH), controller.barState.value)
    }

    @Test
    fun `a late duplicate auto-finished emission after done does not resend`() = runTest {
        // ENC-155/ENC-160: the slowest ordering — the session-first send
        // already reached Done (with its reason) before the emission
        // lands. The emission is the same recording, so it is ignored.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "capped words"
        fakeSession.stopResult =
            StopResult(floatArrayOf(0.4f, 0.5f), AutoFinishReason.MAX_LENGTH)

        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()
        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(
            RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH),
            controller.barState.value,
        )

        fakeSession.emitAutoStopped()
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("capped words"), fakeSink.transcripts)
        assertEquals(
            RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH),
            controller.barState.value,
        )
    }

    @Test
    fun `a silence auto-finish names the dead air in done`() = runTest {
        // ENC-160: the reason rides the stop result — dead air lands on
        // Done(SILENCE), not a plain check.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "trailing thought"
        val samples = floatArrayOf(0.7f, 0.6f)
        fakeSession.stopResult = StopResult(samples, AutoFinishReason.SILENCE)

        fakeSession.emitAutoStopped(samples, AutoFinishReason.SILENCE)
        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("trailing thought"), fakeSink.transcripts)
        assertEquals(
            RecordingBarState.Done(autoFinish = AutoFinishReason.SILENCE),
            controller.barState.value,
        )
    }

    @Test
    fun `a repeated auto-finished emission for finished samples never transcribes twice`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "capped words"
        val cappedSamples = floatArrayOf(0.9f, 0.8f)
        fakeSession.stopResult = StopResult(cappedSamples, AutoFinishReason.MAX_LENGTH)

        fakeSession.emitAutoStopped(cappedSamples)
        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()
        assertEquals(1, fakeEngine.transcribeCallCount)

        fakeSession.emitAutoStopped(cappedSamples)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("capped words"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(autoFinish = AutoFinishReason.MAX_LENGTH), controller.barState.value)
    }

    @Test
    fun `a session ending with no samples while recording returns the bar to idle`() = runTest {
        // ENC-155: a cancelled session has nothing to send — the bar
        // falls back to Idle, and the discard is logged, not silent.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSession.stopFailure = IllegalStateException("nothing active")

        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(0, fakeEngine.transcribeCallCount)
        assertTrue(fakeSink.transcripts.isEmpty())
        assertEquals(RecordingBarState.Idle, controller.barState.value)
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
    fun `a password hide during recording cancels and never transcribes`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)

        controller.onHostHidden(isPasswordFocus = true)

        assertEquals(1, fakeSession.cancelCallCount)
        assertEquals(RecordingBarState.Idle, controller.barState.value)

        runCurrent()
        assertEquals(0, fakeEngine.transcribeCallCount)
        assertTrue(fakeSink.transcripts.isEmpty())
    }

    @Test
    fun `a non-password hide during recording finishes the send instead of cancelling`() = runTest {
        // ENC-156: a shade peek is not a password field — the live
        // recording finishes through the stop path and transcribes.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "shade peek words"

        controller.onHostHidden(isPasswordFocus = false)
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)

        runCurrent()

        assertEquals(0, fakeSession.cancelCallCount)
        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("shade peek words"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(), controller.barState.value)
    }

    @Test
    fun `a password hide during transcribing discards the transcript`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        val gate = CompletableDeferred<Unit>()
        fakeEngine.gate = gate

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)
        assertEquals(1, fakeEngine.transcribeCallCount)

        controller.onHostHidden(isPasswordFocus = true)
        runCurrent()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
        assertTrue(fakeSink.transcripts.isEmpty())
        gate.complete(Unit)
    }

    @Test
    fun `a non-password hide during transcribing lets the send finish`() = runTest {
        // ENC-156: the in-flight send is never cancelled for a
        // non-password cause — the transcript still lands.
        val controller = createController(backgroundScope)
        startRecording(controller)
        val gate = CompletableDeferred<Unit>()
        fakeEngine.gate = gate

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Transcribing, controller.barState.value)

        controller.onHostHidden(isPasswordFocus = false)
        runCurrent()

        assertEquals(RecordingBarState.Transcribing, controller.barState.value)
        assertTrue(fakeSink.transcripts.isEmpty())

        gate.complete(Unit)
        runCurrent()

        assertEquals(RecordingBarState.Done(), controller.barState.value)
        assertEquals(listOf("hello world"), fakeSink.transcripts)
    }

    @Test
    fun `the session ending externally while recording finishes the send`() = runTest {
        // ENC-155: a session end under a live bar is a finish, not a
        // drop — the stopped samples take the same send path as a tap.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeEngine.nextTranscript = "capture tail"

        fakeSession.setState(RecordingSessionState.Idle)
        runCurrent()

        assertEquals(1, fakeEngine.transcribeCallCount)
        assertEquals(listOf("capture tail"), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(), controller.barState.value)
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
        assertEquals(RecordingBarState.Done(), controller.barState.value)

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
            RecordingBarState.Recording(elapsedMs = 500L, frame = 1L),
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
        assertEquals(RecordingBarState.Done(), controller.barState.value)
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
        assertEquals(RecordingBarState.Done(), controller.barState.value)
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
        assertEquals(RecordingBarState.Done(), controller.barState.value)
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
        assertEquals(RecordingBarState.Done(), controller.barState.value)
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
    fun `a password hide during the error window drops the retained retry`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("paste exploded")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        controller.onHostHidden(isPasswordFocus = true)
        assertEquals(RecordingBarState.Idle, controller.barState.value)

        // A tap now starts a new recording; the retained samples are gone.
        fakeSession.setState(RecordingSessionState.Idle)
        controller.onBubbleTap()
        runCurrent()
        assertEquals(2, fakeSession.startCallCount)
        assertEquals(1, fakeEngine.transcribeCallCount)
    }

    @Test
    fun `a non-password hide during the error window keeps the retained retry`() = runTest {
        // ENC-156: a shade peek must not silently discard the retained
        // samples — the Error state and the one retry tap survive it.
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeSink.nextResult = InsertionResult.Failed("paste exploded")

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        controller.onHostHidden(isPasswordFocus = false)
        runCurrent()
        assertEquals(RecordingBarState.Error, controller.barState.value)

        fakeSink.nextResult =
            InsertionResult.Inserted(InsertionResult.Inserted.Method.PASTE)
        controller.onBubbleTap()
        runCurrent()

        assertEquals(2, fakeEngine.transcribeCallCount)
        assertEquals(RecordingBarState.Done(), controller.barState.value)
    }

    @Test
    fun `send with an ai style armed shows transcribing then polishing then done`() = runTest {
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        fakeStylePreference.setSelected("professional")
        fakeApiKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.Styled(text = "Polished words.", llmMs = 900L)
        val gate = CompletableDeferred<Unit>()
        fakeLlm.gate = gate

        controller.onSendTap()
        runCurrent()

        // The LLM call is in flight: the bar must name the stage it is in.
        assertEquals(RecordingBarState.Polishing, controller.barState.value)
        assertEquals(1, fakeLlm.callCount)
        assertTrue(fakeSink.transcripts.isEmpty())

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("Polished words."), fakeSink.transcripts)
        assertEquals(RecordingBarState.Done(), controller.barState.value)
        val stages = emissions.filter {
            it == RecordingBarState.Transcribing ||
                it == RecordingBarState.Polishing ||
                it == RecordingBarState.Done()
        }
        assertEquals(
            listOf(
                RecordingBarState.Transcribing,
                RecordingBarState.Polishing,
                RecordingBarState.Done(),
            ),
            stages,
        )
    }

    @Test
    fun `send with a local style never shows polishing`() = runTest {
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        // Even with a key stored, the default style is mechanical.
        fakeApiKeys.setKey("test-key")

        controller.onSendTap()
        runCurrent()

        assertEquals(RecordingBarState.Done(), controller.barState.value)
        assertTrue(emissions.contains(RecordingBarState.Transcribing))
        assertTrue(!emissions.contains(RecordingBarState.Polishing), "got $emissions")
        assertEquals(0, fakeLlm.callCount)
    }

    @Test
    fun `send with an ai style but no key never shows polishing`() = runTest {
        val controller = createController(backgroundScope)
        val emissions = mutableListOf<RecordingBarState>()
        backgroundScope.launch { controller.barState.collect { emissions.add(it) } }
        startRecording(controller)
        fakeStylePreference.setSelected("professional")

        controller.onSendTap()
        runCurrent()

        assertEquals(RecordingBarState.Done(), controller.barState.value)
        assertTrue(!emissions.contains(RecordingBarState.Polishing), "got $emissions")
        assertEquals(0, fakeLlm.callCount)
    }

    @Test
    fun `an insertion failure after polishing shows error`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeStylePreference.setSelected("professional")
        fakeApiKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.Styled(text = "Polished words.", llmMs = 10L)
        fakeSink.nextResult = InsertionResult.Failed("paste exploded")
        val gate = CompletableDeferred<Unit>()
        fakeLlm.gate = gate

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Polishing, controller.barState.value)

        gate.complete(Unit)
        runCurrent()

        // The failure landed from the polishing stage: the error state is
        // reachable after the label flipped, and the styled text was the
        // text the sink refused.
        assertEquals(RecordingBarState.Error, controller.barState.value)
        assertEquals(listOf("Polished words."), fakeSink.transcripts)
    }

    @Test
    fun `a password hide during polishing discards the transcript`() = runTest {
        val controller = createController(backgroundScope)
        startRecording(controller)
        fakeStylePreference.setSelected("professional")
        fakeApiKeys.setKey("test-key")
        fakeLlm.outcome = LlmStyleOutcome.Styled(text = "Polished words.", llmMs = 10L)
        val gate = CompletableDeferred<Unit>()
        fakeLlm.gate = gate

        controller.onSendTap()
        runCurrent()
        assertEquals(RecordingBarState.Polishing, controller.barState.value)
        assertEquals(1, fakeLlm.callCount)

        controller.onHostHidden(isPasswordFocus = true)
        runCurrent()

        assertEquals(RecordingBarState.Idle, controller.barState.value)
        assertTrue(fakeSink.transcripts.isEmpty())
        gate.complete(Unit)
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
        assertEquals(
            RecordingBarState.Recording(elapsedMs = 500L, frame = 1L),
            controller.barState.value,
        )
    }

    private companion object {
        const val DONE_DISPLAY_MS: Long = 100L
        const val ERROR_DISPLAY_MS: Long = 200L
    }
}
