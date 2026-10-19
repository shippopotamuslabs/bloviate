package com.shippopotamus.bloviate.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** One HTTP round trip's outcome: the status code plus the raw body. */
data class HttpResult(
    val status: Int,
    val body: String,
)

/**
 * The single-shot OpenRouter transport seam (ENC-73). A `fun interface`
 * so unit tests fake the network without touching sockets; the production
 * implementation is plain `HttpURLConnection` — Bloviate has no OkHttp and
 * gains none for one POST per dictation.
 */
fun interface StyleHttpTransport {

    /** POSTs [requestJson] as a chat-completion and returns status + body. */
    suspend fun postChatCompletion(apiKey: String, requestJson: String): HttpResult
}

/** [StyleHttpTransport] over [HttpURLConnection] (see `ModelFileDownloader` for the house pattern). */
class HttpURLConnectionStyleTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StyleHttpTransport {

    override suspend fun postChatCompletion(apiKey: String, requestJson: String): HttpResult =
        withContext(ioDispatcher) {
            val connection = URL(OpenRouterPayload.ENDPOINT_PATH).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = POST_METHOD
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("HTTP-Referer", APP_REFERER)
                connection.setRequestProperty("X-Title", APP_TITLE)
                val payload = requestJson.toByteArray(StandardCharsets.UTF_8)
                connection.outputStream.use { out -> out.write(payload) }
                val status = connection.responseCode
                val stream = if (status in SUCCESS_RANGE) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in SUCCESS_RANGE) {
                    // The status, never the body or key, is the log surface.
                    Log.w(TAG, "OpenRouter style call failed with HTTP $status")
                }
                HttpResult(status = status, body = body)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TAG: String = "OpenRouterStyle"
        const val POST_METHOD: String = "POST"
        const val APP_REFERER: String = "https://github.com/browntown-development/bloviate"
        const val APP_TITLE: String = "Bloviate"

        /** Worst case ~4s on the wire; the client caps the whole call at 4s too. */
        const val CONNECT_TIMEOUT_MS: Int = 1_500
        const val READ_TIMEOUT_MS: Int = 2_500

        val SUCCESS_RANGE: IntRange = HttpURLConnection.HTTP_OK..HttpURLConnection.HTTP_MULT_CHOICE - 1
    }
}
