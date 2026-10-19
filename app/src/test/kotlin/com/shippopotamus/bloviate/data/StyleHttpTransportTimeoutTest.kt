package com.shippopotamus.bloviate.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The socket-vs-client budget pin, born in ENC-83 and re-based in
 * ENC-81. ENC-83's bug: two nested caps, the wrong one binding — the
 * transport's 2.5s socket read timeout pre-empted the client's 4s total
 * cap on a silent single-shot response, so every slow-but-successful
 * style call fell back. ENC-81 replaced the single total cap with
 * liveness budgets judged by the client (first-token / inter-token
 * gap, plus a generous absolute backstop). The invariant survives the
 * redesign: the socket timeout is a backstop for an abandoned read and
 * must never bind before the client's own gap budget — and it must
 * stay below the absolute cap, or an abandoned stream could outlive
 * the call that gave up on it.
 */
class StyleHttpTransportTimeoutTest {

    @Test
    fun `the socket read timeout never binds before the client gap budget`() {
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS > OpenRouterStyleClient.TOKEN_GAP_TIMEOUT_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would pre-empt " +
                "the client's ${OpenRouterStyleClient.TOKEN_GAP_TIMEOUT_MS}ms gap budget",
        )
    }

    @Test
    fun `the socket read timeout stays below the client absolute cap`() {
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS < OpenRouterStyleClient.ABSOLUTE_CAP_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would outlive " +
                "the client's ${OpenRouterStyleClient.ABSOLUTE_CAP_MS}ms absolute cap",
        )
    }

    @Test
    fun `the connect timeout stays the shorter backstop`() {
        assertTrue(
            HttpURLConnectionStyleTransport.CONNECT_TIMEOUT_MS <
                HttpURLConnectionStyleTransport.READ_TIMEOUT_MS,
        )
    }
}
