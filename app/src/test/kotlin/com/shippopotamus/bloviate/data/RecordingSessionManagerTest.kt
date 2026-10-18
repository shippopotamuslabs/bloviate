package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.testing.FakeAudioCapturePort
import com.shippopotamus.bloviate.testing.FakeMicPermissionPort
import com.shippopotamus.bloviate.testing.FakeActiveModelStorePort
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingSessionManagerTest {

    private val fakeCapture = FakeAudioCapturePort()
    private val fakeModelStore = FakeActiveModelStorePort()
    private val fakeMicPermission = FakeMicPermissionPort()

    @Test
    fun `state is unavailable until the model is ready and the mic is granted`() = runTest {
        val session = createSession()
        runCurrent()
        assertEquals(RecordingSessionState.Unavailable, session.state.value)

        fakeModelStore.setReady()
        runCurrent()
        assertEquals(RecordingSessionState.Unavailable, session.state.value)

        fakeMicPermission.setGranted(true)
        runCurrent()
        assertEquals(RecordingSessionState.Idle, session.state.value)
    }

    @Test
    fun `state flow publishes unavailable idle recording idle across a session`() = runTest {
        val session = createSession()
        val emissions = mutableListOf<RecordingSessionState>()
        backgroundScope.launch { session.state.collect { state -> emissions += state } }
        runCurrent()

        makeAvailable()
        runCurrent()

        session.start()
        runCurrent()

        fakeCapture.emit(loudChunk(milliseconds = 100))
        runCurrent()
        // Amplitude publishes ride the ~30 Hz ticker (ENC-66), not the chunk.
        advanceTimeBy(150)
        runCurrent()

        session.stop()
        runCurrent()

        assertEquals(
            listOf(
                RecordingSessionState.Unavailable,
                RecordingSessionState.Idle,
                RecordingSessionState.Recording(elapsedMs = 0L, amplitude = 0f),
                RecordingSessionState.Recording(elapsedMs = 100L, amplitude = 0.5f),
                RecordingSessionState.Idle,
            ),
            emissions,
        )
    }

    @Test
    fun `start is a no-op when unavailable`() = runTest {
        val session = createSession()
        runCurrent()

        session.start()
        runCurrent()

        assertEquals(RecordingSessionState.Unavailable, session.state.value)
        assertEquals(0, fakeCapture.collectionCount)
    }

    @Test
    fun `double start does not restart capture`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()

        session.start()
        runCurrent()
        session.start()
        runCurrent()

        assertEquals(1, fakeCapture.collectionCount)
        assertTrue(session.state.value is RecordingSessionState.Recording)
    }

    @Test
    fun `one chunk publishes many levels as the ticker drains its windows`() = runTest {
        // ENC-66 regression test for the ~1 Hz waveform: amplitude used to
        // publish once per capture chunk (one whole-chunk RMS), so a ~1 s
        // chunk meant one level per second. Levels now slice into ~33 ms
        // windows and publish on a ~30 Hz ticker.
        makeAvailable()
        val session = createSession()
        val emissions = mutableListOf<RecordingSessionState>()
        backgroundScope.launch { session.state.collect { state -> emissions += state } }
        runCurrent()
        session.start()
        runCurrent()

        // A single 990 ms chunk alternating loud/silent 33 ms windows.
        fakeCapture.emit(alternatingChunk())
        runCurrent()
        // The chunk alone publishes nothing new: levels wait for the ticker.
        assertEquals(
            RecordingSessionState.Recording(elapsedMs = 0L, amplitude = 0f),
            session.state.value,
        )

        advanceTimeBy(1_000)
        runCurrent()

        val recordingEmissions = emissions.filterIsInstance<RecordingSessionState.Recording>()
        // ~30 ticker publishes from the one chunk (StateFlow dedupes only
        // *consecutive* equals; the alternating windows never repeat).
        assertTrue(
            recordingEmissions.size >= 25,
            "expected ~30 level publishes from one chunk, got ${recordingEmissions.size}",
        )
        val windowEmissions = recordingEmissions.filter { it.elapsedMs == 990L }
        assertEquals(15, windowEmissions.count { it.amplitude == 0.6f })
        assertEquals(15, windowEmissions.count { it.amplitude == 0f })
        session.cancel()
    }

    @Test
    fun `window slicing carries a partial window across chunk boundaries`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        session.start()
        runCurrent()

        // Two 50 ms chunks: neither fills many windows alone, but together
        // they make 100 ms of audio (~3 windows of 33 ms).
        fakeCapture.emit(loudChunk(milliseconds = 50))
        fakeCapture.emit(loudChunk(milliseconds = 50))
        runCurrent()
        advanceTimeBy(150)
        runCurrent()

        assertEquals(
            RecordingSessionState.Recording(elapsedMs = 100L, amplitude = 0.5f),
            session.state.value,
        )
        session.cancel()
    }

    @Test
    fun `stop returns the buffered samples with silence trimmed`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        session.start()
        runCurrent()

        fakeCapture.emit(silentChunk(milliseconds = 500))
        fakeCapture.emit(loudChunk(milliseconds = 200))
        fakeCapture.emit(silentChunk(milliseconds = 500))
        runCurrent()

        val samples = session.stop()

        // 500 ms silence + 200 ms speech + 500 ms silence, padding 100 ms kept per side.
        assertEquals(6_400, samples.size)
        assertEquals(0f, samples[0])
        assertEquals(0.5f, samples[1_600])
        assertEquals(0.5f, samples[4_799])
        assertEquals(0f, samples[4_800])
        assertEquals(RecordingSessionState.Idle, session.state.value)
    }

    @Test
    fun `stop with no active recording throws`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()

        val thrown = runCatchingStop(session)
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `cancel discards the recording and stop afterwards throws`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        session.start()
        runCurrent()
        fakeCapture.emit(loudChunk(milliseconds = 100))
        runCurrent()

        session.cancel()
        runCurrent()

        assertEquals(RecordingSessionState.Idle, session.state.value)
        val emitted = async { session.autoStoppedRecordings.first() }
        runCurrent()
        assertFalse(emitted.isCompleted)
        emitted.cancel()
        assertTrue(runCatchingStop(session) is IllegalStateException)
    }

    @Test
    fun `hard cap finishes as a stop, emits samples, and a late stop still returns them`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        session.start()
        val autoStopped = async { session.autoStoppedRecordings.first() }
        runCurrent()

        repeat(MAX_SECONDS) { fakeCapture.emit(loudChunk(milliseconds = 1_000)) }
        runCurrent()

        val cappedSamples = autoStopped.await()
        assertEquals(MAX_SAMPLES, cappedSamples.size)
        assertEquals(RecordingSessionState.Idle, session.state.value)

        val lateStopSamples = session.stop()
        assertEquals(MAX_SAMPLES, lateStopSamples.size)
        assertTrue(runCatchingStop(session) is IllegalStateException)
    }

    @Test
    fun `losing availability mid-idle returns the state to unavailable`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        assertEquals(RecordingSessionState.Idle, session.state.value)

        fakeMicPermission.setGranted(false)
        runCurrent()
        assertEquals(RecordingSessionState.Unavailable, session.state.value)
    }

    private fun makeAvailable() {
        fakeModelStore.setReady()
        fakeMicPermission.setGranted(true)
    }

    private fun createSessionInScope(scope: kotlinx.coroutines.CoroutineScope): RecordingSessionManager =
        RecordingSessionManager(
            audioCapture = fakeCapture,
            modelStore = fakeModelStore,
            micPermission = fakeMicPermission,
            sessionScope = scope,
        )

    private fun kotlinx.coroutines.test.TestScope.createSession(): RecordingSessionManager =
        createSessionInScope(backgroundScope)

    private suspend fun runCatchingStop(session: RecordingSessionManager): Throwable? =
        try {
            session.stop()
            null
        } catch (throwable: Throwable) {
            throwable
        }

    /** 990 ms of audio: thirty 33 ms windows alternating 0.6 / silence. */
    private fun alternatingChunk(): FloatArray {
        val windowSamples = 528
        return FloatArray(windowSamples * 30) { index ->
            if ((index / windowSamples) % 2 == 0) 0.6f else 0f
        }
    }

    private fun loudChunk(milliseconds: Int): FloatArray =
        FloatArray(milliseconds * SAMPLES_PER_MS) { 0.5f }

    private fun silentChunk(milliseconds: Int): FloatArray =
        FloatArray(milliseconds * SAMPLES_PER_MS) { 0f }

    private companion object {
        const val SAMPLES_PER_MS: Int = 16
        const val MAX_SECONDS: Int = 180
        const val MAX_SAMPLES: Int = 2_880_000
    }
}
