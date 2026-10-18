package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.testing.FakeModelFileDownloader
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class WhisperModelStoreTest {

    @TempDir
    lateinit var tempDir: File

    private val fakeDownloader = FakeModelFileDownloader()

    /** Synthetic "model" bytes; the store is constructed expecting exactly these. */
    private val payload: ByteArray = "fake-whisper-model-bytes".toByteArray()
    private val payloadSha256: String = sha256Hex(payload)

    @Test
    fun `absent model reports Absent and no path`() = runTest {
        val store = createStore()

        assertEquals(ModelState.Absent, store.state.value)
        assertNull(store.verifiedModelPath())
        assertEquals(ModelState.Absent, store.state.value)
    }

    @Test
    fun `ensureModel downloads verifies and atomically finalizes the model`() = runTest {
        fakeDownloader.bytesToWrite = payload
        val store = createStore()

        store.ensureModel()

        assertEquals(ModelState.Ready, store.state.value)
        assertEquals(1, fakeDownloader.callCount)
        assertTrue(fakeDownloader.writtenTargets.single().name.endsWith(".part"))
        val finalFile = modelFile()
        assertTrue(finalFile.exists())
        assertTrue(finalFile.readBytes().contentEquals(payload))
        assertFalse(tempFile().exists())
        assertEquals(finalFile.absolutePath, store.verifiedModelPath())
    }

    @Test
    fun `hash mismatch fails with the integrity reason and does not retry`() = runTest {
        fakeDownloader.bytesToWrite = "fake-whisper-model-bytex".toByteArray() // same length, wrong bytes
        val store = createStore()

        store.ensureModel()

        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertTrue((state as ModelState.Failed).reason.contains("integrity", ignoreCase = true))
        // A completed download with a wrong hash is not a transport failure:
        // the retry budget must not be burned on it.
        assertEquals(1, fakeDownloader.callCount)
        assertFalse(modelFile().exists())
        assertFalse(tempFile().exists())
        assertNull(store.verifiedModelPath())
    }

    @Test
    fun `size mismatch fails with the integrity reason and does not retry`() = runTest {
        fakeDownloader.bytesToWrite = "too-short".toByteArray()
        val store = createStore()

        store.ensureModel()

        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertTrue((state as ModelState.Failed).reason.contains("integrity", ignoreCase = true))
        assertEquals(1, fakeDownloader.callCount)
        assertFalse(modelFile().exists())
        assertFalse(tempFile().exists())
    }

    @Test
    fun `persistent download failure retries the full budget then reports Failed with the reason`() = runTest {
        fakeDownloader.failure = IOException("network down")
        val store = createStore()

        store.ensureModel()

        assertEquals(WhisperModelStore.MAX_DOWNLOAD_ATTEMPTS, fakeDownloader.callCount)
        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        val reason = (state as ModelState.Failed).reason
        assertTrue(reason.contains("IOException"), "reason should name the failure kind: $reason")
        assertTrue(reason.contains("network down"), "reason should carry the message: $reason")
        assertFalse(modelFile().exists())
        assertFalse(tempFile().exists())
    }

    @Test
    fun `transport failures are retried and a later success finalizes the model`() = runTest {
        fakeDownloader.scriptedFailureCount = WhisperModelStore.MAX_DOWNLOAD_ATTEMPTS - 1
        fakeDownloader.bytesToWrite = payload
        val store = createStore()

        store.ensureModel()

        assertEquals(WhisperModelStore.MAX_DOWNLOAD_ATTEMPTS, fakeDownloader.callCount)
        assertEquals(ModelState.Ready, store.state.value)
        assertTrue(modelFile().readBytes().contentEquals(payload))
    }

    @Test
    fun `an existing verified file skips the download entirely`() = runTest {
        modelFile().parentFile?.mkdirs()
        modelFile().writeBytes(payload)
        fakeDownloader.bytesToWrite = "never-used".toByteArray()
        val store = createStore()

        assertEquals(ModelState.Ready, store.state.value)
        store.ensureModel()

        assertEquals(0, fakeDownloader.callCount)
        assertEquals(ModelState.Ready, store.state.value)
    }

    @Test
    fun `verifiedModelPath deletes a corrupt existing file and refuses it`() = runTest {
        modelFile().parentFile?.mkdirs()
        modelFile().writeBytes("fake-whisper-model-bytex".toByteArray())
        val store = createStore()

        val path = store.verifiedModelPath()

        assertNull(path)
        assertFalse(modelFile().exists())
        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertTrue((state as ModelState.Failed).reason.contains("integrity", ignoreCase = true))
    }

    @Test
    fun `ensureModel replaces a corrupt existing file with a fresh download`() = runTest {
        modelFile().parentFile?.mkdirs()
        modelFile().writeBytes("fake-whisper-model-bytex".toByteArray())
        fakeDownloader.bytesToWrite = payload
        val store = createStore()

        store.ensureModel()

        assertEquals(1, fakeDownloader.callCount)
        assertEquals(ModelState.Ready, store.state.value)
        assertTrue(modelFile().readBytes().contentEquals(payload))
    }

    private fun createStore(): WhisperModelStore =
        WhisperModelStore(
            modelDir = modelDir(),
            downloader = fakeDownloader,
            expectedSizeBytes = payload.size.toLong(),
            expectedSha256 = payloadSha256,
            ioDispatcher = UnconfinedTestDispatcher(),
            retryDelaysMs = listOf(0L, 0L),
        )

    private fun modelDir(): File = File(tempDir, "models")

    private fun modelFile(): File = File(modelDir(), WhisperModelStore.MODEL_FILE_NAME)

    private fun tempFile(): File = File(modelDir(), "ggml-base.en.bin.part")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
