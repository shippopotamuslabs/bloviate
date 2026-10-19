package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.evaluator.LlmResponseCleaner
import com.shippopotamus.bloviate.domain.evaluator.StylePromptBuilder
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeStyleHttpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class OpenRouterStyleClientTest {

    private val professional = StyleCatalog.byId("professional")
    private val fakeTransport = FakeStyleHttpTransport()
    private val fakeKeys = FakeApiKeyPort(initialKey = "test-key")
    private var nowMs = 1_000L

    /** The producer side gets a test dispatcher so stream timing is virtual and deterministic. */
    private fun newClient(producerDispatcher: CoroutineDispatcher) = OpenRouterStyleClient(
        transport = fakeTransport,
        prompts = StylePromptBuilder(),
        cleaner = LlmResponseCleaner(),
        apiKeys = fakeKeys,
        clock = { nowMs },
        producerDispatcher = producerDispatcher,
    )

    @Test
    fun `success assembles the chunked answer and reports the elapsed time`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            // The network takes 800ms on the injected clock.
            nowMs = 1_800L
            emitStream(emit, parts = listOf("Polished ", "text."))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("Polished text.", styled.text)
        assertEquals(800L, styled.llmMs)
    }

    @Test
    fun `a preamble in the assembled answer is stripped before returning`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf("Here is the rewrite:\nPolished text."))
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Polished text.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `no key falls back without any http call`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeKeys.setKey(null)

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-key"), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `blank text passes through without any http call`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))

        val outcome = client.style(style = professional, text = "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a server error falls back with its status`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 500, body = "boom") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "http-500", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
    }

    @Test
    fun `the responding provider is surfaced on success`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-80: streamed chunks carry the serving upstream; telemetry
        // surfaces it so instability is visible from logcat.
        fakeTransport.handler = { _, _, emit ->
            emitStream(emit, parts = listOf("Polished text."), provider = "xAI")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("xAI", styled.provider)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
    }

    @Test
    fun `the responding provider is surfaced on an http failure`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ ->
            HttpResult(status = 500, body = "{\"provider\":\"Claude Platform on AWS\",\"error\":{}}")
        }

        val outcome = client.style(style = professional, text = "raw words")

        val fellBack = outcome as LlmStyleOutcome.FellBack
        assertEquals("http-500", fellBack.reason)
        assertEquals("Claude Platform on AWS", fellBack.provider)
    }

    @Test
    fun `each attempt pins its provider routing and requests the stream`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-80 pins + ENC-81 streaming, asserted on the recorded bodies.
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                HttpResult(status = 404, body = "model not found")
            } else {
                emitStream(emit, parts = listOf("Fallback polish."))
                HttpResult(status = 200, body = "")
            }
        }

        client.style(style = professional, text = "raw words")

        assertTrue(fakeTransport.requests[0].contains("\"stream\":true"))
        assertTrue(fakeTransport.requests[0].contains("\"provider\":{\"order\":[\"xai\"],\"allow_fallbacks\":true}"))
        assertTrue(
            fakeTransport.requests[1].contains(
                "\"provider\":{\"order\":[\"anthropic\"],\"allow_fallbacks\":true,\"ignore\":[\"claude-on-aws\"]}",
            ),
        )
    }

    @Test
    fun `the chain attempts grok latest first and haiku second`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-84: literal chain order — Grok alias, then Haiku, then local.
        fakeTransport.handler = { callIndex, requestJson, emit ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains("~x-ai/grok-latest"))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains("anthropic/claude-haiku-5.5"))
                emitStream(emit, parts = listOf("Haiku polish."))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Haiku polish.", (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[0].contains("~x-ai/grok-latest"))
        assertTrue(fakeTransport.requests[1].contains("anthropic/claude-haiku-5.5"))
    }

    @Test
    fun `a missing primary model retries the fallback model once`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, requestJson, emit ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains(OpenRouterPayload.PRIMARY_MODEL))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains(OpenRouterPayload.FALLBACK_MODEL))
                emitStream(emit, parts = listOf("Fallback polish."))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Fallback polish.", (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `two missing models fall back`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ -> HttpResult(status = 404, body = "model not found") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "model-unavailable", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a long stream with steady tokens succeeds past the old total cap`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // ENC-81's money test (and ENC-83's regression in streamed form):
        // tokens keep arriving inside the gap budget for ~6s of virtual
        // time — past the old 4s total cap AND the old 2.5s socket
        // budget — and the call must succeed. Liveness, not total time.
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("Long "))
            repeat(3) {
                delay(TRICKLE_GAP_MS)
                emit(chunk("polish "))
            }
            delay(TRICKLE_GAP_MS)
            emit(chunk("done."))
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Long polish polish polish done.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `a stream that never starts falls back on the first token budget`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // Both models stay silent past the first-token budget: the chain
        // gives up with the liveness reason, not after the absolute cap.
        fakeTransport.handler = { _, _, _ ->
            delay(NEVER_STARTS_MS)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "first-token-timeout", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a mid-stream stall retries the fallback model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit(chunk("Partial "))
                delay(NEVER_STARTS_MS)
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf("Recovered polish."))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        // The fragment from the stalled stream is never used (atomicity).
        assertEquals("Recovered polish.", (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a mid-stream stall on both models falls back as a stall`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("Partial "))
            delay(NEVER_STARTS_MS)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "stream-stall", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
    }

    @Test
    fun `an error payload mid-stream is a failure, never content`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, emit ->
            emit(chunk("Partial "))
            emit("data: {\"error\":{\"message\":\"provider exploded\",\"code\":500}}")
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "stream-error", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
        assertEquals(1, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a model-unavailable stream error retries the fallback model`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { callIndex, _, emit ->
            if (callIndex == 0) {
                emit("data: {\"error\":{\"message\":\"Model not found\",\"code\":404}}")
                HttpResult(status = 200, body = "")
            } else {
                emitStream(emit, parts = listOf("Fallback polish."))
                HttpResult(status = 200, body = "")
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Fallback polish.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `a transport exception falls back`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        fakeTransport.handler = { _, _, _ -> throw IOException("network down") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "transport-error"), outcome)
    }

    @Test
    fun `a stream with no content falls back as an empty response`() = runTest {
        val client = newClient(StandardTestDispatcher(testScheduler))
        // A 200 stream that delivers no tokens at all (the streamed form
        // of the old unparseable-body case) is an empty response.
        fakeTransport.handler = { _, _, emit ->
            emit(DONE_LINE)
            HttpResult(status = 200, body = "")
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "empty-response", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
    }

    /** One SSE chunk line carrying [content] (and optionally the serving [provider]). */
    private fun chunk(content: String, provider: String? = null): String {
        val providerField = if (provider == null) "" else "\"provider\":\"" + provider + "\","
        return "data: {" + providerField +
            "\"choices\":[{\"delta\":{\"content\":" + OpenRouterPayload.escape(content) + "}}]}"
    }

    /** Emits [parts] as chunks, then the [DONE] terminator. */
    private suspend fun emitStream(
        emit: suspend (String) -> Unit,
        parts: List<String>,
        provider: String? = null,
    ) {
        parts.forEachIndexed { index, part ->
            emit(chunk(part, provider = if (index == 0) provider else null))
        }
        emit(DONE_LINE)
    }

    private companion object {
        const val DONE_LINE: String = "data: [DONE]"
        const val TRICKLE_GAP_MS: Long = 1_500L
        const val NEVER_STARTS_MS: Long = 30_000L
    }
}
