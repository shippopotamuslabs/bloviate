package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.testing.FakeModelStorePort
import com.shippopotamus.bloviate.testing.FakeWhisperEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnDeviceWhisperTranscriptionEngineTest {

    private val fakeWhisperEngine = FakeWhisperEngine()
    private val fakeModelStore = FakeModelStorePort()
    private val engine = OnDeviceWhisperTranscriptionEngine(fakeWhisperEngine, fakeModelStore)

    @Test
    fun `mode is on-device only`() {
        assertEquals(ProcessingMode.ON_DEVICE, engine.mode)
    }

    @Test
    fun `warm up loads the verified model once and stays warm`() = runTest {
        fakeModelStore.setReady()

        engine.warmUp()
        engine.warmUp()

        assertEquals(1, fakeWhisperEngine.loadCount)
        assertEquals(FakeModelStorePort.FAKE_MODEL_PATH, fakeWhisperEngine.lastLoadedPath)
        assertEquals("en", fakeWhisperEngine.lastParams?.language)
        assertTrue(engine.isWarm())
    }

    @Test
    fun `warm up without a verified model loads nothing`() = runTest {
        engine.warmUp()

        assertEquals(0, fakeWhisperEngine.loadCount)
        assertFalse(engine.isWarm())
    }

    @Test
    fun `transcribe warms lazily and reuses the warm context across calls`() = runTest {
        fakeModelStore.setReady()

        val first = engine.transcribe(FloatArray(1_600))
        val second = engine.transcribe(FloatArray(3_200))

        assertEquals("fake transcript", first)
        assertEquals("fake transcript", second)
        assertEquals(1, fakeWhisperEngine.loadCount)
        assertEquals(listOf(1_600, 3_200), fakeWhisperEngine.transcribedSampleCounts)
    }

    @Test
    fun `transcribe without a verified model throws`() = runTest {
        val thrown = try {
            engine.transcribe(FloatArray(1_600))
            null
        } catch (throwable: Throwable) {
            throwable
        }
        assertTrue(thrown is IllegalStateException)
        assertEquals(0, fakeWhisperEngine.loadCount)
    }

    @Test
    fun `release closes the context and the next transcribe reloads it`() = runTest {
        fakeModelStore.setReady()
        engine.warmUp()

        engine.release()

        assertEquals(1, fakeWhisperEngine.closeCount)
        assertFalse(engine.isWarm())

        engine.transcribe(FloatArray(100))
        assertEquals(2, fakeWhisperEngine.loadCount)
        assertTrue(engine.isWarm())
    }

    @Test
    fun `release when not warm is a no-op`() = runTest {
        engine.release()

        assertEquals(0, fakeWhisperEngine.closeCount)
    }
}
