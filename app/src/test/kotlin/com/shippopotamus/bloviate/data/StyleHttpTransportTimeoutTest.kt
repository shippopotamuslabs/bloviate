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
 * the call that gave up on it. ENC-97 made the absolute cap
 * per-attempt (the 14s chain-wide shape starved slow-but-healthy
 * fallbacks); the pins below follow the cap to its new home and add
 * the backstop arithmetic.
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
    fun `the socket read timeout never binds before the client pre-content budgets`() {
        // ENC-91: the pre-content budgets grew past the old socket
        // value — the pin follows them, or the ENC-83 failure returns
        // wearing a reasoning model's clothes.
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS > OpenRouterStyleClient.FIRST_TOKEN_TIMEOUT_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would pre-empt " +
                "the client's ${OpenRouterStyleClient.FIRST_TOKEN_TIMEOUT_MS}ms first-token budget",
        )
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS > OpenRouterStyleClient.REASONING_GAP_TIMEOUT_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would pre-empt " +
                "the client's ${OpenRouterStyleClient.REASONING_GAP_TIMEOUT_MS}ms reasoning budget",
        )
    }

    @Test
    fun `the socket read timeout stays below the client absolute cap`() {
        assertTrue(
            HttpURLConnectionStyleTransport.READ_TIMEOUT_MS < OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS,
            "read timeout ${HttpURLConnectionStyleTransport.READ_TIMEOUT_MS}ms would outlive " +
                "the client's ${OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS}ms per-attempt cap",
        )
    }

    @Test
    fun `the per-attempt cap clears the longest liveness wait with headroom`() {
        // ENC-97 (replaces the ENC-94 chain-cap pin, whose premise was
        // the chain-wide shape): an attempt's death must always be
        // reported by its liveness budgets, never pre-empted by its
        // own absolute cap — the cap exists only for streams that keep
        // showing signs of life yet never finish.
        assertTrue(
            OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS > 3 * OpenRouterStyleClient.FIRST_TOKEN_TIMEOUT_MS,
            "per-attempt cap ${OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS}ms would swallow " +
                "an attempt's ${OpenRouterStyleClient.FIRST_TOKEN_TIMEOUT_MS}ms first-token death",
        )
    }

    @Test
    fun `the chain backstop exceeds the sum of the per-attempt caps`() {
        // ENC-97: the backstop is pathology insurance only. If it
        // could ever bind inside the sum of the four attempt caps
        // (primary/fallback × full/degraded), it would reintroduce
        // the chain-wide starvation this ticket removes.
        assertTrue(
            OpenRouterStyleClient.CHAIN_BACKSTOP_MS > 4 * OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS,
            "chain backstop ${OpenRouterStyleClient.CHAIN_BACKSTOP_MS}ms must exceed the sum of " +
                "the four ${OpenRouterStyleClient.ATTEMPT_ABSOLUTE_CAP_MS}ms attempt caps",
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
