package com.shippopotamus.bloviate.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

/**
 * [ModelFileDownloader] over [HttpURLConnection]. Redirects are followed
 * MANUALLY (not via `instanceFollowRedirects`) so every hop is explicit and
 * logged under the `ModelStore` tag: the Hugging Face resolve URL 302s to a
 * signed CDN URL, and a silent redirect failure was undiagnosable on device
 * (ENC-64). Redirect policy lives in [RedirectResolver].
 */
class HttpModelFileDownloader(
    private val url: String = WhisperModelStore.MODEL_DOWNLOAD_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelFileDownloader {

    override suspend fun downloadTo(
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        withContext(ioDispatcher) {
            var currentUrl = url
            var redirectsFollowed = 0
            while (true) {
                val nextUrl = downloadHop(currentUrl, redirectsFollowed, target, onProgress)
                if (nextUrl == null) return@withContext
                redirectsFollowed++
                currentUrl = nextUrl
            }
        }
    }

    /**
     * One HTTP hop: on a redirect, logs the hop and returns the next URL;
     * on HTTP 200, streams the body to [target] and returns null; any other
     * status throws an [IOException] naming the status and URL.
     */
    private suspend fun downloadHop(
        currentUrl: String,
        redirectsFollowed: Int,
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): String? {
        val connection = URL(currentUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val statusCode = connection.responseCode
            val nextUrl = RedirectResolver.nextUrl(
                currentUrl = currentUrl,
                statusCode = statusCode,
                location = connection.getHeaderField(LOCATION_HEADER),
                redirectsFollowed = redirectsFollowed,
            )
            if (nextUrl != null) {
                Log.d(
                    WhisperModelStore.TAG,
                    "Model download redirect HTTP $statusCode: $currentUrl -> $nextUrl",
                )
                return nextUrl
            }
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Model download failed with HTTP $statusCode from $currentUrl")
            }
            val totalBytes = connection.contentLengthLong
            Log.d(
                WhisperModelStore.TAG,
                "Model download connected: HTTP $statusCode, $totalBytes bytes, final URL $currentUrl",
            )
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
            return null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS: Int = 30_000
        const val READ_TIMEOUT_MS: Int = 30_000
        const val BUFFER_SIZE_BYTES: Int = 64 * 1024
        const val USER_AGENT: String = "Bloviate-Android"
        const val LOCATION_HEADER: String = "Location"
    }
}
