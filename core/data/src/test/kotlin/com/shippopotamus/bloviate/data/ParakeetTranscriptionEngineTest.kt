package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.ProcessingMode
import com.shippopotamus.bloviate.testing.FakeParakeetModelStorePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParakeetTranscriptionEngineTest {

    private val fakeModelStore = FakeParakeetModelStorePort()
    private val fakeFactory = FakeParakeetRecognizerFactory()
    private val engine = ParakeetTranscriptionEngine(fakeModelStore).apply {
        recognizerFactory = fakeFactory::create
    }

    @Test
    fun `mode is on-device only`() {
        assertEquals(ProcessingMode.ON_DEVICE, engine.mode)
    }

    @Test
    fun `warm up creates the recognizer once from the verified files and stays warm`() = runTest {
        fakeModelStore.setReady()

        engine.warmUp()
        engine.warmUp()

        assertEquals(listOf(FakeParakeetModelStorePort.FAKE_MODEL_FILES), fakeFactory.createdWith)
        assertTrue(engine.isWarm())
    }

    @Test
    fun `warm up without a verified set creates nothing`() = runTest {
        engine.warmUp()

        assertTrue(fakeFactory.createdWith.isEmpty())
        assertFalse(engine.isWarm())
    }

    @Test
    fun `transcribe without a verified set throws instead of guessing`() = runTest {
        var thrown: Throwable? = null
        try {
            engine.transcribe(FloatArray(1_600))
        } catch (exception: IllegalStateException) {
            thrown = exception
        }
        assertTrue(thrown is IllegalStateException, "expected IllegalStateException, was $thrown")
    }

    @Test
    fun `transcribe warms lazily and reuses the warm recognizer across calls`() = runTest {
        fakeModelStore.setReady()
        fakeFactory.nextTranscript = "hello from parakeet"

        val first = engine.transcribe(FloatArray(1_600))
        val second = engine.transcribe(FloatArray(3_200))

        assertEquals("hello from parakeet", first)
        assertEquals("hello from parakeet", second)
        assertEquals(1, fakeFactory.createdWith.size)
        val recognizer = fakeFactory.recognizers.single()
        assertEquals(2, recognizer.transcribeCallCount)
        assertEquals(3_200, recognizer.lastSamplesSize)
    }

    @Test
    fun `release closes the recognizer and the next transcribe re-creates it`() = runTest {
        fakeModelStore.setReady()

        engine.transcribe(FloatArray(1_600))
        engine.release()

        assertEquals(1, fakeFactory.recognizers.single().closeCount)
        assertFalse(engine.isWarm())

        engine.transcribe(FloatArray(1_600))

        assertEquals(2, fakeFactory.createdWith.size)
        assertTrue(engine.isWarm())
    }
}
