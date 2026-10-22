package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.data.HttpResult
import com.shippopotamus.bloviate.data.StyleHttpTransport

/**
 * Hand-written fake of [StyleHttpTransport]. [handler] scripts one
 * result per call (receiving the call index, the request JSON, and the
 * line emitter for streamed successes); every request body is recorded
 * in [requests]. [keylessCallCount] counts calls without retaining
 * the key value.
 */
class FakeStyleHttpTransport(
    var handler: suspend (
        callIndex: Int,
        requestJson: String,
        emit: suspend (String) -> Unit,
    ) -> HttpResult =
        { _, _, _ -> HttpResult(status = 500, body = "") },
) : StyleHttpTransport {

    private val mutableRequests = mutableListOf<String>()

    /** Request bodies posted so far, in order. */
    val requests: List<String> get() = mutableRequests.toList()

    /** Number of calls made without recording a key. */
    var keylessCallCount: Int = 0
        private set

    var lastApiKeyWasBlank: Boolean? = null
        private set

    override suspend fun postChatCompletion(
        apiKey: String,
        requestJson: String,
        onEventLine: suspend (String) -> Unit,
    ): HttpResult {
        lastApiKeyWasBlank = apiKey.isBlank()
        keylessCallCount++
        // Record BEFORE the handler runs: a streamed call can be
        // cancelled by the consumer the moment it is satisfied ([DONE]
        // / liveness), and the request still happened.
        val callIndex = mutableRequests.size
        mutableRequests += requestJson
        return handler(callIndex, requestJson, onEventLine)
    }
}
