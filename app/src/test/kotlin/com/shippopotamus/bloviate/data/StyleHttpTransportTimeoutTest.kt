package com.shippopotamus.bloviate.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ENC-83 regression pin: the style call's budget is owned by
 * `OpenRouterStyleClient`'s coroutine cap (4s). A non-streamed chat
 * completion sends no bytes until generation finishes, so if the
 * transport's socket read timeout is shorter than realistic generation
 * (observed 3.3–4.0s server-side), it pre-empts the client cap and every
 * slow-but-successful call silently falls back. The socket timeouts are
 * backstops and must never bind first.
 */
class StyleHttpTransportTimeoutTest {

    @Test
    fun `the socket read timeout never binds before the client cap`() {
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS >= CLIENT_CAP_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would pre-empt " +
                "the client's ${CLIENT_CAP_MS}ms cap",
        )
    }

    @Test
    fun `the connect timeout stays the shorter backstop`() {
        assertTrue(
            HttpURLConnectionStyleTransport.CONNECT_TIMEOUT_MS <
                HttpURLConnectionStyleTransport.READ_TIMEOUT_MS,
        )
    }

    private companion object {
        /** Mirrors OpenRouterStyleClient's documented whole-call cap. */
        const val CLIENT_CAP_MS: Int = 4_000
    }
}
