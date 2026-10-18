package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * [ModelStorePort] for the pinned Whisper model: ggml-base.en in app-private
 * storage. Downloads land in a `.part` temp file, are SHA-256 verified
 * against the pinned digest, and are renamed into place — a partial or
 * unverified file is never visible to the engine (`contracts/DATA_LAYER_RULES.md`
 * §4). Verification is cached for the process lifetime; the load gate is
 * [verifiedModelPath], which re-verifies after any doubt.
 *
 * This is the placeholder the ENC-55 download stack replaces; it stays small
 * on purpose.
 */
class WhisperModelStore(
    private val modelDir: File,
    private val downloader: ModelFileDownloader,
    private val expectedSizeBytes: Long = EXPECTED_SIZE_BYTES,
    private val expectedSha256: String = EXPECTED_SHA256,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelStorePort {

    private val ensureMutex = Mutex()

    @Volatile
    private var verifiedThisProcess: Boolean = false

    private val mutableState = MutableStateFlow<ModelState>(
        if (modelFile().exists()) ModelState.Ready else ModelState.Absent,
    )

    override val state: StateFlow<ModelState> = mutableState.asStateFlow()

    override suspend fun ensureModel() {
        ensureMutex.withLock {
            if (verifiedModelPath() != null) return
            downloadAndFinalize()
        }
    }

    override suspend fun verifiedModelPath(): String? =
        withContext(ioDispatcher) {
            val file = modelFile()
            if (!file.exists()) {
                if (mutableState.value !is ModelState.Downloading) {
                    mutableState.update { ModelState.Absent }
                }
                return@withContext null
            }
            if (verifiedThisProcess) return@withContext file.absolutePath
            if (verifyIntegrity(file)) {
                verifiedThisProcess = true
                mutableState.update { ModelState.Ready }
                Log.d(TAG, "Model verified: ${file.name} ($expectedSizeBytes bytes, SHA-256 ok)")
                file.absolutePath
            } else {
                Log.e(TAG, "Model failed integrity check; deleting ${file.absolutePath}")
                file.delete()
                verifiedThisProcess = false
                mutableState.update { ModelState.Failed }
                null
            }
        }

    private suspend fun downloadAndFinalize() {
        val tempFile = tempModelFile()
        try {
            withContext(ioDispatcher) {
                modelDir.mkdirs()
                val freeBytes = modelDir.usableSpace
                check(freeBytes > expectedSizeBytes + FREE_SPACE_MARGIN_BYTES) {
                    "Not enough free space for the model: $freeBytes bytes available"
                }
            }
            mutableState.update { ModelState.Downloading(progressFraction = 0f) }
            Log.d(TAG, "Downloading model from $MODEL_DOWNLOAD_URL")
            downloader.downloadTo(tempFile) { downloadedBytes, totalBytes ->
                val fraction = when {
                    totalBytes > 0 -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                    expectedSizeBytes > 0 -> (downloadedBytes.toFloat() / expectedSizeBytes).coerceIn(0f, 1f)
                    else -> 0f
                }
                mutableState.update { current ->
                    if (current is ModelState.Downloading &&
                        fraction - current.progressFraction < PROGRESS_UPDATE_STEP
                    ) {
                        current
                    } else {
                        ModelState.Downloading(progressFraction = fraction)
                    }
                }
            }
            val finalized = withContext(ioDispatcher) {
                if (!verifyIntegrity(tempFile)) {
                    Log.e(TAG, "Downloaded model failed integrity check; discarding")
                    tempFile.delete()
                    false
                } else if (!tempFile.renameTo(modelFile())) {
                    // Same-directory rename is atomic on Android; fall back to copy+delete.
                    tempFile.copyTo(modelFile(), overwrite = true)
                    tempFile.delete()
                    true
                } else {
                    true
                }
            }
            if (finalized) {
                verifiedThisProcess = true
                mutableState.update { ModelState.Ready }
                Log.d(TAG, "Model downloaded and finalized: ${modelFile().absolutePath}")
            } else {
                mutableState.update { ModelState.Failed }
            }
        } catch (exception: CancellationException) {
            discardFailedDownload(tempFile)
            Log.d(TAG, "Model download cancelled")
            throw exception
        } catch (exception: Exception) {
            discardFailedDownload(tempFile)
            Log.e(TAG, "Model download failed", exception)
        }
    }

    /** Deletes the partial download and reports failure; completes even when the caller was cancelled. */
    private suspend fun discardFailedDownload(tempFile: File) {
        withContext(NonCancellable) {
            tempFile.delete()
        }
        mutableState.update { ModelState.Failed }
    }

    /** Size + SHA-256 against the pinned values. Runs on the caller's (IO) context. */
    private fun verifyIntegrity(file: File): Boolean {
        if (file.length() != expectedSizeBytes) {
            Log.w(TAG, "Model size mismatch: ${file.length()} != $expectedSizeBytes")
            return false
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE_BYTES)
            while (true) {
                val readCount = input.read(buffer)
                if (readCount < 0) break
                digest.update(buffer, 0, readCount)
            }
        }
        val actualSha256 = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        return actualSha256.equals(expectedSha256, ignoreCase = true)
    }

    private fun modelFile(): File = File(modelDir, MODEL_FILE_NAME)

    private fun tempModelFile(): File = File(modelDir, TEMP_MODEL_FILE_NAME)

    companion object {
        const val TAG: String = "ModelStore"

        /** Pinned model identity (ggml-base.en from the whisper.cpp HF repo). */
        const val MODEL_FILE_NAME: String = "ggml-base.en.bin"
        const val EXPECTED_SIZE_BYTES: Long = 147_964_211L
        const val EXPECTED_SHA256: String =
            "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"
        const val MODEL_DOWNLOAD_URL: String =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

        private const val TEMP_MODEL_FILE_NAME: String = "ggml-base.en.bin.part"
        private const val FREE_SPACE_MARGIN_BYTES: Long = 32L * 1024 * 1024
        private const val HASH_BUFFER_SIZE_BYTES: Int = 64 * 1024
        private const val PROGRESS_UPDATE_STEP: Float = 0.01f
    }
}
