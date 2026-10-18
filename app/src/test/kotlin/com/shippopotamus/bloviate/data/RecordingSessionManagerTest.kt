package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.RecordingSessionState
import com.shippopotamus.bloviate.testing.FakeAudioCapturePort
import com.shippopotamus.bloviate.testing.FakeMicPermissionPort
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingSessionManagerTest {

    private val fakeCapture = FakeAudioCapturePort()
    private val fakeModelStore = FakeModelStorePort()
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
    fun `recording state throttles elapsed updates to a UI-sane rate`() = runTest {
        makeAvailable()
        val session = createSession()
        runCurrent()
        session.start()
        runCurrent()

        fakeCapture.emit(loudChunk(milliseconds = 100))
        runCurrent()
        assertEquals(
            RecordingSessionState.Recording(elapsedMs = 100L, amplitude = 0.5f),
            session.state.value,
        )

        fakeCapture.emit(loudChunk(milliseconds = 50))
        runCurrent()
        // Only 50 ms of new audio since the last publish (< the 80 ms interval).
        assertEquals(
            RecordingSessionState.Recording(elapsedMs = 100L, amplitude = 0.5f),
            session.state.value,
        )

        fakeCapture.emit(loudChunk(milliseconds = 50))
        runCurrent()
        assertEquals(
            RecordingSessionState.Recording(elapsedMs = 200L, amplitude = 0.5f),
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
