package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.evaluator.LlmResponseCleaner
import com.shippopotamus.bloviate.domain.evaluator.StylePromptBuilder
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import com.shippopotamus.bloviate.domain.port.LlmStyleOutcome
import com.shippopotamus.bloviate.testing.FakeApiKeyPort
import com.shippopotamus.bloviate.testing.FakeStyleHttpTransport
import kotlinx.coroutines.delay
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
    private val client = OpenRouterStyleClient(
        transport = fakeTransport,
        prompts = StylePromptBuilder(),
        cleaner = LlmResponseCleaner(),
        apiKeys = fakeKeys,
        clock = { nowMs },
    )

    @Test
    fun `success parses the answer and reports the elapsed time`() = runTest {
        fakeTransport.handler = { _, _ ->
            // The network takes 800ms on the injected clock.
            nowMs = 1_800L
            HttpResult(status = 200, body = assistantBody("Polished text."))
        }

        val outcome = client.style(style = professional, text = "raw words")

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("Polished text.", styled.text)
        assertEquals(800L, styled.llmMs)
    }

    @Test
    fun `a preamble in the answer is stripped before returning`() = runTest {
        fakeTransport.handler = { _, _ ->
            HttpResult(status = 200, body = assistantBody("Here is the rewrite:\\nPolished text."))
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Polished text.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `no key falls back without any http call`() = runTest {
        fakeKeys.setKey(null)

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-key"), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `blank text passes through without any http call`() = runTest {
        val outcome = client.style(style = professional, text = "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertEquals(0, fakeTransport.keylessCallCount)
    }

    @Test
    fun `a server error falls back with its status`() = runTest {
        fakeTransport.handler = { _, _ -> HttpResult(status = 500, body = "boom") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "http-500", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
    }

    @Test
    fun `the responding provider is surfaced on success`() = runTest {
        // ENC-80: OpenRouter stamps the serving upstream on the response;
        // telemetry carries it so instability is visible from logcat.
        fakeTransport.handler = { _, _ ->
            HttpResult(status = 200, body = assistantBody("Polished text.", provider = "xAI"))
        }

        val outcome = client.style(style = professional, text = "raw words")

        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("xAI", styled.provider)
        assertEquals(OpenRouterPayload.PRIMARY_MODEL, styled.model)
    }

    @Test
    fun `the responding provider is surfaced on an http failure`() = runTest {
        fakeTransport.handler = { _, _ ->
            HttpResult(status = 500, body = "{\"provider\":\"Claude Platform on AWS\",\"error\":{}}")
        }

        val outcome = client.style(style = professional, text = "raw words")

        val fellBack = outcome as LlmStyleOutcome.FellBack
        assertEquals("http-500", fellBack.reason)
        assertEquals("Claude Platform on AWS", fellBack.provider)
    }

    @Test
    fun `each attempt pins its provider routing`() = runTest {
        // ENC-80: primary pins the xAI family; the Haiku retry prefers
        // Anthropic first-party and ignores Claude Platform on AWS.
        fakeTransport.handler = { callIndex, _ ->
            if (callIndex == 0) {
                HttpResult(status = 404, body = "model not found")
            } else {
                HttpResult(status = 200, body = assistantBody("Fallback polish."))
            }
        }

        client.style(style = professional, text = "raw words")

        assertTrue(fakeTransport.requests[0].contains("\"provider\":{\"order\":[\"xai\"],\"allow_fallbacks\":true}"))
        assertTrue(
            fakeTransport.requests[1].contains(
                "\"provider\":{\"order\":[\"anthropic\"],\"allow_fallbacks\":true,\"ignore\":[\"claude-on-aws\"]}",
            ),
        )
    }

    @Test
    fun `a slow call inside the hard cap still succeeds`() = runTest {
        // ENC-83 regression: real OpenRouter generations take 3.3-4.0s
        // server-side. A call that lands inside the client's 4s cap must
        // succeed — the old failure was the transport's 2.5s socket read
        // timeout pre-empting this cap (pinned in StyleHttpTransportTimeoutTest).
        fakeTransport.handler = { _, _ ->
            delay(SLOW_IN_BUDGET_MS)
            HttpResult(status = 200, body = assistantBody("Slow polish."))
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Slow polish.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `a hung call falls back on the hard timeout`() = runTest {
        fakeTransport.handler = { _, _ ->
            delay(HUNG_CALL_MS)
            HttpResult(status = 200, body = assistantBody("Too late."))
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "timeout"), outcome)
    }

    @Test
    fun `a transport exception falls back`() = runTest {
        fakeTransport.handler = { _, _ -> throw IOException("network down") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "transport-error"), outcome)
    }

    @Test
    fun `a missing primary model retries the fallback model once`() = runTest {
        fakeTransport.handler = { callIndex, requestJson ->
            if (callIndex == 0) {
                // The first request must name the primary model...
                assertTrue(requestJson.contains(OpenRouterPayload.PRIMARY_MODEL))
                HttpResult(status = 404, body = "model not found")
            } else {
                // ...and the single retry must name the fallback.
                assertTrue(requestJson.contains(OpenRouterPayload.FALLBACK_MODEL))
                HttpResult(status = 200, body = assistantBody("Fallback polish."))
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Fallback polish.", (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `the chain attempts grok latest first and haiku second`() = runTest {
        // ENC-84: literal chain order — Grok alias, then Haiku, then local.
        fakeTransport.handler = { callIndex, requestJson ->
            if (callIndex == 0) {
                assertTrue(requestJson.contains("~x-ai/grok-latest"))
                HttpResult(status = 404, body = "model not found")
            } else {
                assertTrue(requestJson.contains("anthropic/claude-haiku-5.5"))
                HttpResult(status = 200, body = assistantBody("Haiku polish."))
            }
        }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals("Haiku polish.", (outcome as LlmStyleOutcome.Styled).text)
        assertEquals(2, fakeTransport.requests.size)
        assertTrue(fakeTransport.requests[0].contains("~x-ai/grok-latest"))
        assertTrue(fakeTransport.requests[1].contains("anthropic/claude-haiku-5.5"))
    }

    @Test
    fun `two missing models fall back`() = runTest {
        fakeTransport.handler = { _, _ -> HttpResult(status = 404, body = "model not found") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "model-unavailable", model = OpenRouterPayload.FALLBACK_MODEL),
            outcome,
        )
        assertEquals(2, fakeTransport.keylessCallCount)
    }

    @Test
    fun `an unparseable success body falls back`() = runTest {
        fakeTransport.handler = { _, _ -> HttpResult(status = 200, body = "{\"error\":true}") }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "bad-response", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
    }

    @Test
    fun `an empty answer after cleaning falls back`() = runTest {
        fakeTransport.handler = { _, _ -> HttpResult(status = 200, body = assistantBody("```\\n```")) }

        val outcome = client.style(style = professional, text = "raw words")

        assertEquals(
            LlmStyleOutcome.FellBack(reason = "empty-response", model = OpenRouterPayload.PRIMARY_MODEL),
            outcome,
        )
    }

    private fun assistantBody(content: String, provider: String? = null): String {
        val providerField = if (provider == null) "" else "\"provider\":\"" + provider + "\","
        return "{" + providerField +
            "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}"
    }

    private companion object {
        const val HUNG_CALL_MS: Long = 30_000L
        const val SLOW_IN_BUDGET_MS: Long = 3_500L
    }
}
