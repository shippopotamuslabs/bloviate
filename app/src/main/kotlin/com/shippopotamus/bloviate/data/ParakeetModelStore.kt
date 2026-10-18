package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.model.ParakeetModelFiles
import com.shippopotamus.bloviate.domain.port.ParakeetModelStorePort
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
 * [ParakeetModelStorePort] for the pinned Parakeet file set (see
 * [ParakeetModelFileSet]) in app-private storage. The multi-file sibling of
 * [WhisperModelStore], with the same discipline and failure semantics
 * (ENC-64): every file downloads to a `.part` temp, is SHA-256 verified
 * against its pinned digest, and is renamed into place atomically; readiness
 * is all-or-nothing, so a partial set is never visible to the engine
 * (`contracts/DATA_LAYER_RULES.md` §4). Transport failures are retried per
 * file up to [MAX_DOWNLOAD_ATTEMPTS] attempts with backoff; a completed
 * download that fails integrity and a free-space shortfall fail immediately
 * with their reason in [ModelState.Failed]. Files already finalized stay on
 * disk — a later ensure resumes with only the missing files.
 *
 * This is the spike's deliberately small store; ENC-55's download stack
 * replaces it behind the same port.
 */
class ParakeetModelStore(
    private val modelDir: File,
    private val downloaderFactory: (url: String) -> ModelFileDownloader,
    private val files: List<ParakeetModelFile> = ParakeetModelFileSet.FILES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) : ParakeetModelStorePort {

    private val ensureMutex = Mutex()

    @Volatile
    private var verifiedThisProcess: Boolean = false

    private val totalSizeBytes: Long = files.sumOf { file -> file.sizeBytes }

    private val mutableState = MutableStateFlow<ModelState>(
        if (files.all { spec -> fileFor(spec).exists() }) ModelState.Ready else ModelState.Absent,
    )

    override val state: StateFlow<ModelState> = mutableState.asStateFlow()

    override suspend fun ensureModel() {
        ensureMutex.withLock {
            if (verifiedModelFiles() != null) return
            downloadAndFinalizeMissing()
        }
    }

    override suspend fun verifiedModelFiles(): ParakeetModelFiles? =
        withContext(ioDispatcher) {
            if (!files.all { spec -> fileFor(spec).exists() }) {
                if (mutableState.value !is ModelState.Downloading) {
                    mutableState.update { ModelState.Absent }
                }
                return@withContext null
            }
            if (verifiedThisProcess) return@withContext modelFiles()
            val corrupt = findCorruptFiles()
            if (corrupt.isEmpty()) {
                verifiedThisProcess = true
                mutableState.update { ModelState.Ready }
                Log.d(TAG, "Parakeet model set verified (${files.size} files, $totalSizeBytes bytes, SHA-256 ok)")
                modelFiles()
            } else {
                deleteCorruptFiles(corrupt)
                null
            }
        }

    private suspend fun downloadAndFinalizeMissing() {
        // Free-space is a precondition, not a transport failure: fail once,
        // with the reason, and never burn download retries on it. Only the
        // missing files count — finalized files are already on disk.
        val missing = files.filterNot { spec -> fileFor(spec).exists() }
        try {
            withContext(ioDispatcher) {
                modelDir.mkdirs()
                val neededBytes = missing.sumOf { spec -> spec.sizeBytes }
                val freeBytes = modelDir.usableSpace
                check(freeBytes > neededBytes + FREE_SPACE_MARGIN_BYTES) {
                    "Not enough free space for the Parakeet model: $freeBytes bytes available, $neededBytes needed"
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Parakeet download precondition failed", exception)
            mutableState.update { ModelState.Failed(failureReason(exception)) }
            return
        }

        var completedBytes = totalSizeBytes - missing.sumOf { spec -> spec.sizeBytes }
        publishProgress(completedBytes)
        for (spec in missing) {
            val finalized = downloadFileWithRetries(spec, completedBytes)
            if (!finalized) return
            completedBytes += spec.sizeBytes
            publishProgress(completedBytes)
        }
        // Readiness is all-or-nothing: re-verify the WHOLE set, including
        // files that were already on disk — a present-but-corrupt file must
        // never ride along on the downloaded files' verification.
        val corrupt = withContext(ioDispatcher) { findCorruptFiles() }
        if (corrupt.isNotEmpty()) {
            withContext(ioDispatcher) { deleteCorruptFiles(corrupt) }
            return
        }
        verifiedThisProcess = true
        mutableState.update { ModelState.Ready }
        Log.d(TAG, "Parakeet model set downloaded and finalized: ${modelDir.absolutePath}")
    }

    /** Files of the set whose on-disk bytes fail their pinned size + SHA-256. Caller runs IO. */
    private fun findCorruptFiles(): List<ParakeetModelFile> =
        files.filterNot { spec -> verifyIntegrity(spec, fileFor(spec)) }

    /** Deletes corrupt set files and publishes the corrupt-model failure. Caller runs IO. */
    private fun deleteCorruptFiles(corrupt: List<ParakeetModelFile>) {
        Log.e(TAG, "Parakeet files failed integrity check; deleting: ${corrupt.map { it.fileName }}")
        corrupt.forEach { spec -> fileFor(spec).delete() }
        verifiedThisProcess = false
        mutableState.update { ModelState.Failed(REASON_CORRUPT_EXISTING_MODEL) }
    }

    /**
     * Downloads one file with bounded transport retries, then verifies and
     * renames it into place. Returns false when the whole ensure must stop:
     * retries exhausted, an integrity failure (terminal, not retried), or
     * cancellation (rethrown instead).
     */
    private suspend fun downloadFileWithRetries(spec: ParakeetModelFile, completedBytesBefore: Long): Boolean {
        val tempFile = tempFileFor(spec)
        var lastFailure: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            if (attempt > 1) {
                val backoffMs = retryDelaysMs.getOrElse(attempt - 2) { retryDelaysMs.lastOrNull() ?: 0L }
                Log.w(
                    TAG,
                    "Retrying Parakeet download of ${spec.fileName} " +
                        "(attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS) after ${backoffMs}ms",
                )
                delay(backoffMs)
            }
            try {
                Log.d(TAG, "Downloading Parakeet file ${spec.fileName} from ${spec.downloadUrl}")
                downloaderFactory(spec.downloadUrl).downloadTo(tempFile) { downloadedBytes, _ ->
                    val inFileBytes = downloadedBytes.coerceIn(0L, spec.sizeBytes)
                    publishProgress(completedBytesBefore + inFileBytes)
                }
            } catch (exception: CancellationException) {
                discardPartialDownload(tempFile)
                mutableState.update { ModelState.Failed(REASON_DOWNLOAD_CANCELLED) }
                Log.d(TAG, "Parakeet download cancelled")
                throw exception
            } catch (exception: Exception) {
                // Transport failure (network error, HTTP status): bounded retry.
                lastFailure = exception
                discardPartialDownload(tempFile)
                Log.e(TAG, "Parakeet download of ${spec.fileName} attempt $attempt of $MAX_DOWNLOAD_ATTEMPTS failed", exception)
                continue
            }
            return finalizeDownloadedFile(spec, tempFile)
        }
        mutableState.update {
            ModelState.Failed(lastFailure?.let(::failureReason) ?: REASON_UNKNOWN_FAILURE)
        }
        return false
    }

    /**
     * Verifies a completed file download and renames it into place. An
     * integrity failure is terminal for this ensure call — a full-length
     * file with a wrong hash is not a transport problem, so no retry.
     */
    private suspend fun finalizeDownloadedFile(spec: ParakeetModelFile, tempFile: File): Boolean {
        val finalized = withContext(ioDispatcher) {
            if (!verifyIntegrity(spec, tempFile)) {
                Log.e(TAG, "Downloaded Parakeet file ${spec.fileName} failed integrity check; discarding")
                tempFile.delete()
                false
            } else if (!tempFile.renameTo(fileFor(spec))) {
                // Same-directory rename is atomic on Android; fall back to copy+delete.
                tempFile.copyTo(fileFor(spec), overwrite = true)
                tempFile.delete()
                true
            } else {
                true
            }
        }
        if (!finalized) {
            mutableState.update { ModelState.Failed(REASON_INTEGRITY_FAILED) }
        }
        return finalized
    }

    /** Deletes the partial download; completes even when the caller was cancelled. */
    private suspend fun discardPartialDownload(tempFile: File) {
        withContext(NonCancellable) {
            tempFile.delete()
        }
    }

    /** Aggregated set progress: finalized bytes plus the in-flight file's bytes, over the set total. */
    private fun publishProgress(completedBytes: Long) {
        val fraction = if (totalSizeBytes > 0) {
            (completedBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        mutableState.update { current ->
            if (current is ModelState.Downloading &&
                fraction - current.progressFraction < PROGRESS_UPDATE_STEP &&
                fraction < 1f
            ) {
                current
            } else {
                ModelState.Downloading(progressFraction = fraction)
            }
        }
    }

    /** Compact "ExceptionKind: message" for [ModelState.Failed]; the UI shows it verbatim. */
    private fun failureReason(exception: Throwable): String {
        val message = exception.message?.takeIf { it.isNotBlank() } ?: "no details"
        return "${exception.javaClass.simpleName}: $message".take(MAX_FAILURE_REASON_LENGTH)
    }

    /** Size + SHA-256 against the file's pinned values. Runs on the caller's (IO) context. */
    private fun verifyIntegrity(spec: ParakeetModelFile, file: File): Boolean {
        if (file.length() != spec.sizeBytes) {
            Log.w(TAG, "Parakeet file ${spec.fileName} size mismatch: ${file.length()} != ${spec.sizeBytes}")
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
        return actualSha256.equals(spec.sha256, ignoreCase = true)
    }

    /** Paths of the full set, by role. Only called once the set is verified complete. */
    private fun modelFiles(): ParakeetModelFiles {
        fun pathOf(pinned: ParakeetModelFile): String {
            val spec = files.firstOrNull { candidate -> candidate.fileName == pinned.fileName } ?: pinned
            return fileFor(spec).absolutePath
        }
        return ParakeetModelFiles(
            encoderPath = pathOf(ParakeetModelFileSet.ENCODER),
            decoderPath = pathOf(ParakeetModelFileSet.DECODER),
            joinerPath = pathOf(ParakeetModelFileSet.JOINER),
            tokensPath = pathOf(ParakeetModelFileSet.TOKENS),
        )
    }

    private fun fileFor(spec: ParakeetModelFile): File = File(modelDir, spec.fileName)

    private fun tempFileFor(spec: ParakeetModelFile): File = File(modelDir, "${spec.fileName}.part")

    companion object {
        const val TAG: String = "ParakeetModelStore"

        private const val FREE_SPACE_MARGIN_BYTES: Long = 32L * 1024 * 1024
        private const val HASH_BUFFER_SIZE_BYTES: Int = 64 * 1024
        private const val PROGRESS_UPDATE_STEP: Float = 0.01f

        /** Total attempts per file for transport failures (1 initial + 2 retries). */
        const val MAX_DOWNLOAD_ATTEMPTS: Int = 3

        /** Backoff before attempts 2 and 3 of a transport-failed file download. */
        val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(2_000L, 5_000L)

        private const val MAX_FAILURE_REASON_LENGTH: Int = 160
        private const val REASON_INTEGRITY_FAILED: String =
            "Downloaded file failed the integrity check (size or SHA-256 mismatch)"
        private const val REASON_CORRUPT_EXISTING_MODEL: String =
            "Existing Parakeet model file failed the integrity check and was deleted"
        private const val REASON_DOWNLOAD_CANCELLED: String = "Download cancelled"
        private const val REASON_UNKNOWN_FAILURE: String = "Download failed for an unknown reason"
    }
}
