package com.shippopotamus.bloviate.data

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * One HTTP round trip's outcome: the status code plus a body. For a
 * streamed success (ENC-81) the body is empty — the content arrives via
 * the transport's line callback; for an HTTP failure the body is the
 * error payload, which the client still mines for provider/model info.
 */
data class HttpResult(
    val status: Int,
    val body: String,
)

/**
 * The OpenRouter transport seam (ENC-73, streamed since ENC-81). A
 * `fun interface` so unit tests fake the network without touching
 * sockets; the production implementation is plain `HttpURLConnection`
 * — Bloviate has no OkHttp and gains none for one POST per dictation.
 */
fun interface StyleHttpTransport {

    /**
     * POSTs [requestJson] as a chat-completion. On a 2xx, each raw SSE
     * line is delivered to [onEventLine] as it arrives (suspending —
     * the consumer's pace applies backpressure); on an HTTP failure no
     * lines are delivered and the result carries the error body.
     */
    suspend fun postChatCompletion(
        apiKey: String,
        requestJson: String,
        onEventLine: suspend (String) -> Unit,
    ): HttpResult
}

/** [StyleHttpTransport] over [HttpURLConnection] (see `ModelFileDownloader` for the house pattern). */
class HttpURLConnectionStyleTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StyleHttpTransport {

    override suspend fun postChatCompletion(
        apiKey: String,
        requestJson: String,
        onEventLine: suspend (String) -> Unit,
    ): HttpResult =
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
                if (status !in SUCCESS_RANGE) {
                    val body = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    // The status, never the body or key, is the log surface.
                    Log.w(TAG, "OpenRouter style call failed with HTTP $status")
                    return@withContext HttpResult(status = status, body = body)
                }
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        onEventLine(line)
                    }
                }
                HttpResult(status = status, body = "")
            } finally {
                connection.disconnect()
            }
        }

    internal companion object {
        const val TAG: String = "OpenRouterStyle"
        const val POST_METHOD: String = "POST"
        const val APP_REFERER: String = "https://github.com/browntown-development/bloviate"
        const val APP_TITLE: String = "Bloviate"

        const val CONNECT_TIMEOUT_MS: Int = 1_500

        /**
         * Socket backstop for the streamed read (ENC-81). Liveness —
         * first-token and inter-token budgets — is judged by the client,
         * so this must sit ABOVE the client's inter-token gap budget
         * (never bind first, the ENC-83 lesson) and BELOW its absolute
         * cap. It exists to bound the abandoned IO thread after the
         * client has already given up on a stalled stream.
         */
        const val READ_TIMEOUT_MS: Int = 5_000

        val SUCCESS_RANGE: IntRange = HttpURLConnection.HTTP_OK..HttpURLConnection.HTTP_MULT_CHOICE - 1
    }
}
