package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.evaluator.PromptMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterPayloadTest {

    private val system = PromptMessage(role = "system", content = "Be brief.")
    private val user = PromptMessage(role = "user", content = "Say hi.")

    @Test
    fun `the style chain is gemini 3 1 flash lite then local`() {
        // ENC-99 as revised (Sean, 2026-10-19): pinning the literal
        // string here is what makes an accidental slug edit a test
        // failure instead of a silent behavior change. There is no
        // second model to pin: the fallback slot is deliberately
        // empty pending Sean's call, so the chain is this slug,
        // then the local text.
        assertEquals("google/gemini-3.1-flash-lite", OpenRouterPayload.PRIMARY_MODEL)
    }

    @Test
    fun `the demoted models are retained for a future engine choice`() {
        // ENC-96/99: each demoted slug stays pinned so the demotion
        // is a deliberate, visible state — not a deletion a revival
        // would have to reconstruct from history. Grok (reasoning
        // latency, ENC-96), Llama (ignored the transcript, ENC-99),
        // Haiku (over-refusal / sanitisation, ENC-99).
        assertEquals("~x-ai/grok-latest", OpenRouterPayload.GROK_MODEL)
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterPayload.LLAMA_MODEL)
        assertEquals("anthropic/claude-haiku-5.5", OpenRouterPayload.HAIKU_MODEL)
    }

    @Test
    fun `the primary provider pool is the standard gemini endpoints latency sorted`() {
        // ENC-99: endpoint tags are the OpenRouter endpoints API's
        // values, verified live at implementation time (all at 100%
        // 30-day uptime). The Flex tags (google-vertex/global/flex,
        // google-ai-studio/flex) and the /priority tags are absent
        // on purpose — an allow-list admits only what it names.
        assertEquals(
            listOf("google-vertex/global", "google-ai-studio", "google-vertex/us", "google-vertex/eu"),
            OpenRouterPayload.PRIMARY_PROVIDER_ONLY,
        )
        assertEquals("latency", OpenRouterPayload.PRIMARY_PROVIDER_SORT)
    }

    @Test
    fun `a request with an only list and a sort carries both in the provider block`() {
        // ENC-99: the primary's exact wire shape — allow-list plus
        // latency sort, fallbacks on within the list.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            providerOnly = OpenRouterPayload.PRIMARY_PROVIDER_ONLY,
            providerSort = OpenRouterPayload.PRIMARY_PROVIDER_SORT,
        )

        assertTrue(
            request.contains(
                "\"provider\":{\"allow_fallbacks\":true," +
                    "\"only\":[\"google-vertex/global\",\"google-ai-studio\",\"google-vertex/us\",\"google-vertex/eu\"]," +
                    "\"sort\":\"latency\"}",
            ),
        )
    }

    @Test
    fun `request names the model and carries both messages`() {
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
        )

        assertTrue(request.contains(OpenRouterPayload.PRIMARY_MODEL))
        assertTrue(request.contains("\"role\":\"system\""))
        assertTrue(request.contains("\"role\":\"user\""))
        assertTrue(request.contains("Be brief."))
        assertTrue(request.contains("Say hi."))
    }

    @Test
    fun `request without a pin carries no provider block`() {
        val request = OpenRouterPayload.buildChatRequest(
            model = "m",
            system = system,
            user = user,
        )

        assertFalse(request.contains("\"provider\""))
    }

    @Test
    fun `request with a pin carries the provider routing block`() {
        val request = OpenRouterPayload.buildChatRequest(
            model = "m",
            system = system,
            user = user,
            providerOrder = listOf("xai"),
        )

        assertTrue(request.contains("\"provider\":{\"order\":[\"xai\"],\"allow_fallbacks\":true}"))
    }

    @Test
    fun `request with an ignore list carries it in the provider block`() {
        val request = OpenRouterPayload.buildChatRequest(
            model = "m",
            system = system,
            user = user,
            providerOrder = listOf("anthropic"),
            providerIgnore = listOf("claude-on-aws"),
        )

        assertTrue(
            request.contains(
                "\"provider\":{\"order\":[\"anthropic\"],\"allow_fallbacks\":true,\"ignore\":[\"claude-on-aws\"]}",
            ),
        )
    }

    @Test
    fun `the responding provider is extracted from a response body`() {
        val body = "{\"id\":\"gen-1\",\"provider\":\"xAI\",\"choices\":[]}"

        assertEquals("xAI", OpenRouterPayload.extractProvider(body))
    }

    @Test
    fun `a provider decoy inside a string value does not resolve`() {
        val body = "{\"error\":{\"message\":\"no \\\"provider\\\" field here\"}}"

        assertNull(OpenRouterPayload.extractProvider(body))
    }

    @Test
    fun `a body without a provider reads as none`() {
        assertNull(OpenRouterPayload.extractProvider("{\"choices\":[]}"))
    }

    @Test
    fun `request escapes quotes and newlines in content`() {
        val tricky = PromptMessage(role = "user", content = "He said \"hi\"\nnew line.")
        val request = OpenRouterPayload.buildChatRequest(
            model = "m",
            system = system,
            user = tricky,
        )

        assertTrue(request.contains("He said \\\"hi\\\"\\nnew line."))
    }

    @Test
    fun `streaming is off unless requested and on when requested`() {
        val plain = OpenRouterPayload.buildChatRequest(model = "m", system = system, user = user)
        val streamed = OpenRouterPayload.buildChatRequest(model = "m", system = system, user = user, stream = true)

        assertFalse(plain.contains("\"stream\""))
        assertTrue(streamed.contains("\"stream\":true"))
    }

    @Test
    fun `the reasoning control pins to effort none`() {
        // ENC-99: the shipped control value, pinned literally for the
        // same reason as the model slugs — a silent edit here changes
        // every style call's latency profile. "none" because the
        // ENC-99 primary's own metadata lists it in supported_efforts
        // with reasoning not mandatory (live-verified); ENC-94's
        // effort "low" was the strongest control the then-primary
        // (Grok 4.7) would honor, and it still thought ~1,000 tokens
        // a call.
        assertEquals("none", OpenRouterPayload.REASONING_EFFORT)
    }

    @Test
    fun `a request carries no reasoning control by default`() {
        // The BUILDER's bare shape: no effort given, no block. The
        // chain never sends this shape — its link passes the effort
        // explicitly on every attempt (ENC-99).
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            providerOnly = OpenRouterPayload.PRIMARY_PROVIDER_ONLY,
            providerSort = OpenRouterPayload.PRIMARY_PROVIDER_SORT,
        )

        assertFalse(request.contains("\"reasoning\""))
    }

    @Test
    fun `a request carries the reasoning control when an effort is given`() {
        // ENC-99: the chain's full-attempt shape — the primary with
        // reasoning explicitly off.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )

        assertTrue(request.contains("\"reasoning\":{\"effort\":\"none\"}"))
    }

    @Test
    fun `the reasoning block never combines effort with a token cap`() {
        // ENC-95's regression, pinned: OpenRouter's unified reasoning
        // parameter accepts effort OR max_tokens, not both — the
        // combined ENC-94 shape is what providers rejected with
        // 400-class errors on device. Whatever else changes, the two
        // must never ride together again.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            stream = true,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )

        assertFalse(request.contains("\"reasoning\":{\"effort\":\"none\",\"max_tokens\""))
        assertFalse(request.contains(",\"max_tokens\":512}"))
    }

    @Test
    fun `a degraded request drops stream options but keeps the reasoning control and still streams`() {
        // ENC-99's degraded shape (ENC-95's mechanism, revised): the
        // retry drops stream_options — the one purely-optional block —
        // but KEEPS reasoning effort none. Dropping it would silently
        // re-enable the primary's default reasoning on exactly the
        // calls that already failed once.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            stream = true,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
            includeUsage = false,
        )

        assertTrue(request.contains("\"stream\":true"))
        assertFalse(request.contains("\"stream_options\""))
        assertTrue(request.contains("\"reasoning\":{\"effort\":\"none\"}"))
    }

    @Test
    fun `error code and type are extracted from an openrouter error body`() {
        // ENC-95 telemetry: provider metadata off a rejected attempt.
        val body = "{\"error\":{\"message\":\"max_tokens must be greater than the thinking budget\"," +
            "\"code\":400,\"type\":\"invalid_request_error\",\"metadata\":{\"provider_name\":\"Anthropic\"}}}"

        val info = OpenRouterPayload.extractErrorInfo(body)
        assertEquals(400, info?.code)
        assertEquals("invalid_request_error", info?.type)
    }

    @Test
    fun `a string error code still resolves to its number`() {
        val body = "{\"error\":{\"code\":\"429\",\"message\":\"rate limited\"}}"

        assertEquals(429, OpenRouterPayload.extractErrorInfo(body)?.code)
    }

    @Test
    fun `a body without an error object reports no error info`() {
        assertNull(OpenRouterPayload.extractErrorInfo("{\"choices\":[]}"))
    }

    @Test
    fun `an error decoy inside a string value does not resolve`() {
        val body = "{\"choices\":[{\"delta\":{\"content\":\"an \\\"error\\\": {\\\"code\\\": 500} here\"}}]}"

        assertNull(OpenRouterPayload.extractErrorInfo(body))
    }

    @Test
    fun `a streamed request asks for usage so reasoning tokens reach telemetry`() {
        // ENC-94: without include_usage the final chunk may carry no
        // usage object, and the reasoning spend stays invisible.
        val plain = OpenRouterPayload.buildChatRequest(model = "m", system = system, user = user)
        val streamed = OpenRouterPayload.buildChatRequest(model = "m", system = system, user = user, stream = true)

        assertFalse(plain.contains("\"stream_options\""))
        assertTrue(streamed.contains("\"stream_options\":{\"include_usage\":true}"))
    }

    @Test
    fun `reasoning tokens are extracted from a streamed usage chunk`() {
        // ENC-94: the closing usage chunk OpenRouter appends when
        // include_usage is on — empty choices, the spend in details.
        val data = "{\"choices\":[],\"usage\":{\"prompt_tokens\":1603,\"completion_tokens\":3282," +
            "\"completion_tokens_details\":{\"reasoning_tokens\":3102}}}"

        assertEquals(3102, OpenRouterPayload.extractReasoningTokens(data))
    }

    @Test
    fun `reasoning tokens are extracted from a non-streamed body`() {
        val body = "{\"choices\":[{\"message\":{\"content\":\"Done.\"}}]," +
            "\"usage\":{\"completion_tokens_details\":{\"reasoning_tokens\":48}}}"

        assertEquals(48, OpenRouterPayload.extractReasoningTokens(body))
    }

    @Test
    fun `a chunk without usage reports no reasoning tokens`() {
        assertNull(OpenRouterPayload.extractReasoningTokens("{\"choices\":[{\"delta\":{\"content\":\"Hi.\"}}]}"))
    }

    @Test
    fun `a reasoning tokens decoy inside a string value does not resolve`() {
        val data = "{\"choices\":[{\"delta\":{\"content\":\"see \\\"reasoning_tokens\\\": 9999 here\"}}]}"

        assertNull(OpenRouterPayload.extractReasoningTokens(data))
    }

    @Test
    fun `a stream delta is extracted from a chunk`() {
        val data = "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"Pol\"}}]}"

        assertEquals("Pol", OpenRouterPayload.extractStreamDelta(data))
        assertFalse(OpenRouterPayload.isStreamError(data))
    }

    @Test
    fun `a role-only opener chunk carries no delta`() {
        val data = "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"

        assertNull(OpenRouterPayload.extractStreamDelta(data))
    }

    @Test
    fun `a chunk without choices carries no delta`() {
        assertNull(OpenRouterPayload.extractStreamDelta("{\"usage\":{\"total_tokens\":9}}"))
    }

    @Test
    fun `an error payload chunk reads as a stream error`() {
        val data = "{\"error\":{\"message\":\"provider exploded\",\"code\":500}}"

        assertTrue(OpenRouterPayload.isStreamError(data))
        assertNull(OpenRouterPayload.extractStreamDelta(data))
    }

    @Test
    fun `the word error inside delta content is not a stream error`() {
        val data = "{\"choices\":[{\"delta\":{\"content\":\"error handling is fun\"}}]}"

        assertFalse(OpenRouterPayload.isStreamError(data))
        assertEquals("error handling is fun", OpenRouterPayload.extractStreamDelta(data))
    }

    @Test
    fun `escaped sequences in a delta are unescaped`() {
        val data = "{\"choices\":[{\"delta\":{\"content\":\"Line one.\\nLine \\\"two\\\".\"}}]}"

        assertEquals("Line one.\nLine \"two\".", OpenRouterPayload.extractStreamDelta(data))
    }

    @Test
    fun `a content decoy inside an error string does not resolve as a delta`() {
        val data = "{\"error\":{\"message\":\"the \\\"content\\\" field is missing\"}}"

        assertNull(OpenRouterPayload.extractStreamDelta(data))
    }

    @Test
    fun `an xai reasoning chunk yields its reasoning text`() {
        // ENC-91: xAI's wire shape — the thought rides in reasoning_content.
        val chunk = "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"Thinking it over.\"}}]}"

        assertEquals("Thinking it over.", OpenRouterPayload.extractReasoningDelta(chunk))
    }

    @Test
    fun `an openrouter reasoning chunk yields its reasoning text`() {
        // ENC-91: OpenRouter's unified shape — plain `reasoning`.
        val chunk = "{\"choices\":[{\"delta\":{\"reasoning\":\"Thinking it over.\"}}]}"

        assertEquals("Thinking it over.", OpenRouterPayload.extractReasoningDelta(chunk))
    }

    @Test
    fun `a reasoning details chunk registers as reasoning`() {
        // ENC-91: structured reasoning entries have no single text
        // field; presence alone is the sign of life.
        val chunk = "{\"choices\":[{\"delta\":{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"...\"}]}}]}"

        assertEquals("", OpenRouterPayload.extractReasoningDelta(chunk))
    }

    @Test
    fun `a content chunk carries no reasoning`() {
        val chunk = "{\"choices\":[{\"delta\":{\"content\":\"The answer.\"}}]}"

        assertNull(OpenRouterPayload.extractReasoningDelta(chunk))
    }

    @Test
    fun `a null reasoning field is not a sign of life`() {
        val chunk = "{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":null,\"reasoning\":null}}]}"

        assertNull(OpenRouterPayload.extractReasoningDelta(chunk))
    }

    @Test
    fun `a non-streamed completion body yields its message content`() {
        // ENC-91: the single-shot shape — message, not delta.
        val body = "{\"id\":\"gen-1\",\"provider\":\"SpaceXAI\",\"choices\":[{\"index\":0," +
            "\"message\":{\"role\":\"assistant\",\"content\":\"The whole answer.\"},\"finish_reason\":\"stop\"}]}"

        assertEquals("The whole answer.", OpenRouterPayload.extractMessageContent(body))
    }

    @Test
    fun `a streamed chunk is not a message body`() {
        val chunk = "{\"choices\":[{\"delta\":{\"content\":\"Partial.\"}}]}"

        assertNull(OpenRouterPayload.extractMessageContent(chunk))
    }

    @Test
    fun `a 404 is model-unavailable`() {
        assertTrue(OpenRouterPayload.isModelUnavailable(404, ""))
    }

    @Test
    fun `a model-not-found error body is model-unavailable`() {
        assertTrue(OpenRouterPayload.isModelUnavailable(400, "{\"error\":{\"message\":\"Model not found\"}}"))
        assertTrue(OpenRouterPayload.isModelUnavailable(200, "No endpoints available for the model"))
    }

    @Test
    fun `a plain server error is not model-unavailable`() {
        assertFalse(OpenRouterPayload.isModelUnavailable(500, "Internal Server Error"))
        assertFalse(OpenRouterPayload.isModelUnavailable(429, "{\"error\":{\"message\":\"Rate limited\"}}"))
    }
}
