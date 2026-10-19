package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.data.HttpResult
import com.shippopotamus.bloviate.data.StyleHttpTransport

/**
 * Hand-written fake of [StyleHttpTransport]. [handler] scripts one result
 * per call (receiving the call index and the request JSON); every request
 * body is recorded in [requests]. [callsWithKey] counts calls without
 * retaining the key value.
 */
class FakeStyleHttpTransport(
    var handler: suspend (callIndex: Int, requestJson: String) -> HttpResult =
        { _, _ -> HttpResult(status = 500, body = "") },
) : StyleHttpTransport {

    private val mutableRequests = mutableListOf<String>()

    /** Request bodies posted so far, in order. */
    val requests: List<String> get() = mutableRequests.toList()

    /** Number of calls made without recording a key. */
    var keylessCallCount: Int = 0
        private set

    var lastApiKeyWasBlank: Boolean? = null
        private set

    override suspend fun postChatCompletion(apiKey: String, requestJson: String): HttpResult {
        lastApiKeyWasBlank = apiKey.isBlank()
        keylessCallCount++
        val result = handler(mutableRequests.size, requestJson)
        mutableRequests += requestJson
        return result
    }
}
