package com.shippopotamus.bloviate.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single file to [target] (a temp file owned by the caller),
 * reporting byte progress. Deliberately minimal — ENC-55's download stack
 * (WorkManager, resumable byte offsets) replaces this behind `ModelStorePort`.
 */
interface ModelFileDownloader {

    /**
     * Streams the file to [target], invoking [onProgress] with
     * (downloadedBytes, totalBytes); totalBytes is -1 when the server does
     * not declare a length.
     *
     * @throws java.io.IOException on any transport or write failure.
     */
    suspend fun downloadTo(
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    )
}

/** [ModelFileDownloader] over [HttpURLConnection]; follows the Hugging Face CDN redirect. */
class HttpModelFileDownloader(
    private val url: String = WhisperModelStore.MODEL_DOWNLOAD_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelFileDownloader {

    override suspend fun downloadTo(
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        withContext(ioDispatcher) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connect()
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Model download failed with HTTP ${connection.responseCode}"
                }
                val totalBytes = connection.contentLengthLong
                target.parentFile?.mkdirs()
                connection.inputStream.buffered().use { input ->
                    FileOutputStream(target).buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        var downloadedBytes = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val readCount = input.read(buffer)
                            if (readCount < 0) break
                            output.write(buffer, 0, readCount)
                            downloadedBytes += readCount
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS: Int = 30_000
        const val READ_TIMEOUT_MS: Int = 30_000
        const val BUFFER_SIZE_BYTES: Int = 64 * 1024
        const val USER_AGENT: String = "Bloviate-Android"
    }
}
