package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
 * Failure semantics (ENC-64): every failure lands in [ModelState.Failed]
 * **with its reason** — the status screen shows it, so an on-device failure
 * is diagnosable without logcat. Transport failures (the downloader threw:
 * network, HTTP status) are retried up to [MAX_DOWNLOAD_ATTEMPTS] attempts
 * with [retryDelaysMs] backoff; a completed download that fails integrity
 * and a free-space shortfall are NOT retried automatically (different
 * signals — re-running `ensureModel` from the UI is the manual retry).
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
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
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
                mutableState.update { ModelState.Failed(REASON_CORRUPT_EXISTING_MODEL) }
                null
            }
        }

    private suspend fun downloadAndFinalize() {
        val tempFile = tempModelFile()
        // Free-space is a precondition, not a transport failure: fail once,
        // with the reason, and never burn download retries on it.
        try {
            withContext(ioDispatcher) {
                modelDir.mkdirs()
                val freeBytes = modelDir.usableSpace
                check(freeBytes > expectedSizeBytes + FREE_SPACE_MARGIN_BYTES) {
                    "Not enough free space for the model: $freeBytes bytes available"
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Model download precondition failed", exception)
            mutableState.update { ModelState.Failed(failureReason(exception)) }
            return
        }

        var lastFailure: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            if (attempt > 1) {
                val backoffMs = retryDelaysMs.getOrElse(attempt - 2) { retryDelaysMs.lastOrNull() ?: 0L }
                Log.w(TAG, "Retrying model download (attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS) after ${backoffMs}ms")
                delay(backoffMs)
            }
            try {
                downloadOnce(tempFile)
            } catch (exception: CancellationException) {
                discardPartialDownload(tempFile)
                mutableState.update { ModelState.Failed(REASON_DOWNLOAD_CANCELLED) }
                Log.d(TAG, "Model download cancelled")
                throw exception
            } catch (exception: Exception) {
                // Transport failure (network error, HTTP status): bounded retry.
                lastFailure = exception
                discardPartialDownload(tempFile)
                Log.e(TAG, "Model download attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS failed", exception)
                continue
            }
            finalizeDownloadedModel(tempFile)
            return
        }
        mutableState.update {
            ModelState.Failed(lastFailure?.let(::failureReason) ?: REASON_UNKNOWN_FAILURE)
        }
    }

    /** One download attempt: streams the model into [tempFile], publishing throttled progress. */
    private suspend fun downloadOnce(tempFile: File) {
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
    }

    /**
     * Verifies a completed download and renames it into place. An integrity
     * failure here is terminal for this ensure call — a full-length file
     * with a wrong hash is not a transport problem, so no automatic retry.
     */
    private suspend fun finalizeDownloadedModel(tempFile: File) {
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
            mutableState.update { ModelState.Failed(REASON_INTEGRITY_FAILED) }
        }
    }

    /** Deletes the partial download; completes even when the caller was cancelled. */
    private suspend fun discardPartialDownload(tempFile: File) {
        withContext(NonCancellable) {
            tempFile.delete()
        }
    }

    /** Compact "ExceptionKind: message" for [ModelState.Failed]; the UI shows it verbatim. */
    private fun failureReason(exception: Throwable): String {
        val message = exception.message?.takeIf { it.isNotBlank() } ?: "no details"
        return "${exception.javaClass.simpleName}: $message".take(MAX_FAILURE_REASON_LENGTH)
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

        /** Total attempts per ensure call for transport failures (1 initial + 2 retries). */
        const val MAX_DOWNLOAD_ATTEMPTS: Int = 3

        /** Backoff before attempts 2 and 3 of a transport-failed download. */
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(2_000L, 5_000L)

        private const val MAX_FAILURE_REASON_LENGTH: Int = 160
        private const val REASON_INTEGRITY_FAILED: String =
            "Downloaded file failed the integrity check (size or SHA-256 mismatch)"
        private const val REASON_CORRUPT_EXISTING_MODEL: String =
            "Existing model file failed the integrity check and was deleted"
        private const val REASON_DOWNLOAD_CANCELLED: String = "Download cancelled"
        private const val REASON_UNKNOWN_FAILURE: String = "Download failed for an unknown reason"
    }
}
