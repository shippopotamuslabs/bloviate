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
import java.security.MessageDigest

class ParakeetModelStoreTest {

    @TempDir
    lateinit var tempDir: File

    /** Synthetic per-file "model" bytes; the store is constructed expecting exactly these. */
    private val payloads: Map<String, ByteArray> = mapOf(
        "encoder.int8.onnx" to "fake-encoder-bytes".toByteArray(),
        "decoder.int8.onnx" to "fake-decoder-bytes".toByteArray(),
        "joiner.int8.onnx" to "fake-joiner-bytes".toByteArray(),
        "tokens.txt" to "fake-tokens".toByteArray(),
    )

    /** The pinned set re-pointed at the synthetic payloads (same names, tiny sizes, real digests). */
    private val syntheticFiles: List<ParakeetModelFile> = ParakeetModelFileSet.FILES.map { pinned ->
        val payload = payloads.getValue(pinned.fileName)
        pinned.copy(
            sizeBytes = payload.size.toLong(),
            sha256 = sha256Hex(payload),
            downloadUrl = "https://example.test/${pinned.fileName}",
        )
    }

    private val downloadersByUrl = mutableMapOf<String, FakeModelFileDownloader>()

    private fun downloaderFor(url: String): FakeModelFileDownloader =
        downloadersByUrl.getOrPut(url) {
            FakeModelFileDownloader().apply {
                bytesToWrite = payloads.getValue(url.substringAfterLast('/'))
            }
        }

    private fun createStore(): ParakeetModelStore =
        ParakeetModelStore(
            modelDir = tempDir,
            downloaderFactory = { url -> downloaderFor(url) },
            files = syntheticFiles,
            ioDispatcher = UnconfinedTestDispatcher(),
            retryDelaysMs = listOf(0L, 0L),
        )

    @Test
    fun `absent set reports Absent and no files`() = runTest {
        val store = createStore()

        assertEquals(ModelState.Absent, store.state.value)
        assertNull(store.verifiedModelFiles())
        assertEquals(ModelState.Absent, store.state.value)
    }

    @Test
    fun `ensureModel downloads verifies and atomically finalizes the whole set`() = runTest {
        val store = createStore()

        store.ensureModel()

        assertEquals(ModelState.Ready, store.state.value)
        for (spec in syntheticFiles) {
            val downloader = downloadersByUrl.getValue(spec.downloadUrl)
            assertEquals(1, downloader.callCount, "downloads of ${spec.fileName}")
            assertTrue(downloader.writtenTargets.single().name.endsWith(".part"))
            val finalFile = File(tempDir, spec.fileName)
            assertTrue(finalFile.exists(), "${spec.fileName} finalized")
            assertTrue(finalFile.readBytes().contentEquals(payloads.getValue(spec.fileName)))
            assertFalse(File(tempDir, "${spec.fileName}.part").exists())
        }
        val files = store.verifiedModelFiles()
        assertEquals(File(tempDir, "encoder.int8.onnx").absolutePath, files?.encoderPath)
        assertEquals(File(tempDir, "tokens.txt").absolutePath, files?.tokensPath)
    }

    @Test
    fun `partial set is not ready and ensure downloads only the missing files`() = runTest {
        // Two of the four files already on disk, byte-perfect.
        writeSetFile("encoder.int8.onnx")
        writeSetFile("tokens.txt")
        val store = createStore()

        assertNull(store.verifiedModelFiles())

        store.ensureModel()

        assertEquals(ModelState.Ready, store.state.value)
        assertTrue(downloadersByUrl.isNotEmpty())
        assertFalse(downloadersByUrl.containsKey("https://example.test/encoder.int8.onnx"))
        assertFalse(downloadersByUrl.containsKey("https://example.test/tokens.txt"))
        assertEquals(1, downloadersByUrl.getValue("https://example.test/decoder.int8.onnx").callCount)
        assertEquals(1, downloadersByUrl.getValue("https://example.test/joiner.int8.onnx").callCount)
    }

    @Test
    fun `a corrupt existing file is deleted and the set reports Failed`() = runTest {
        // Full set on disk, but the joiner's bytes are wrong (same length).
        for (spec in syntheticFiles) {
            if (spec.fileName == "joiner.int8.onnx") {
                File(tempDir, spec.fileName).writeBytes("fake-joiner-bytex".toByteArray())
            } else {
                writeSetFile(spec.fileName)
            }
        }
        val store = createStore()

        assertNull(store.verifiedModelFiles())

        assertFalse(File(tempDir, "joiner.int8.onnx").exists())
        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
    }

    @Test
    fun `a corrupt present file cannot ride along when the rest downloads`() = runTest {
        // Partial set: tokens present but corrupt; the other three download fine.
        File(tempDir, "tokens.txt").writeBytes("fake-tokenx".toByteArray())
        val store = createStore()

        store.ensureModel()

        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertNull(store.verifiedModelFiles())
        assertFalse(File(tempDir, "tokens.txt").exists())
        // The good downloads stay on disk for the next ensure.
        assertTrue(File(tempDir, "encoder.int8.onnx").exists())
    }

    @Test
    fun `hash mismatch on download fails with the integrity reason and does not retry`() = runTest {
        downloaderFor("https://example.test/decoder.int8.onnx").bytesToWrite =
            "fake-decoder-bytex".toByteArray() // same length, wrong bytes
        val store = createStore()

        store.ensureModel()

        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertTrue((state as ModelState.Failed).reason.contains("integrity", ignoreCase = true))
        assertEquals(1, downloadersByUrl.getValue("https://example.test/decoder.int8.onnx").callCount)
        assertFalse(File(tempDir, "decoder.int8.onnx").exists())
        assertNull(store.verifiedModelFiles())
    }

    @Test
    fun `transport failures retry per file and then succeed`() = runTest {
        downloaderFor("https://example.test/joiner.int8.onnx").scriptedFailureCount = 2
        val store = createStore()

        store.ensureModel()

        assertEquals(ModelState.Ready, store.state.value)
        assertEquals(3, downloadersByUrl.getValue("https://example.test/joiner.int8.onnx").callCount)
    }

    @Test
    fun `exhausted transport retries fail with the transport reason`() = runTest {
        downloaderFor("https://example.test/encoder.int8.onnx").scriptedFailureCount = 99
        val store = createStore()

        store.ensureModel()

        val state = store.state.value
        assertTrue(state is ModelState.Failed, "expected Failed, was $state")
        assertEquals(ParakeetModelStore.MAX_DOWNLOAD_ATTEMPTS, downloadersByUrl.getValue("https://example.test/encoder.int8.onnx").callCount)
        assertFalse(File(tempDir, "encoder.int8.onnx").exists())
    }

    private fun writeSetFile(fileName: String) {
        File(tempDir, fileName).writeBytes(payloads.getValue(fileName))
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
