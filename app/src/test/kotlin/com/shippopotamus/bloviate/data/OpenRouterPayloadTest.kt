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
    fun `the style chain is llama 3 3 then haiku 5 5 then local`() {
        // ENC-96 (Sean, 2026-10-19): pinning the literal strings here
        // is what makes an accidental slug edit a test failure instead
        // of a silent behavior change.
        assertEquals("meta-llama/llama-3.3-70b-instruct", OpenRouterPayload.PRIMARY_MODEL)
        assertEquals("anthropic/claude-haiku-5.5", OpenRouterPayload.FALLBACK_MODEL)
    }

    @Test
    fun `the demoted grok alias is retained for a future engine choice`() {
        // ENC-96: Grok left the default chain over reasoning latency;
        // its slug stays pinned so the demotion is a deliberate,
        // visible state — not a deletion a revival would have to
        // reconstruct from history.
        assertEquals("~x-ai/grok-latest", OpenRouterPayload.GROK_MODEL)
    }

    @Test
    fun `the primary provider order is the fast silicon pool with groq at the head`() {
        // ENC-96: slugs are the OpenRouter providers API's canonical
        // values, verified live at implementation time — Groq first
        // (Sean's pick), then SambaNova, Together, DeepInfra.
        assertEquals(
            listOf("groq", "sambanova", "together", "deepinfra"),
            OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
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
    fun `the reasoning control pins to effort low`() {
        // ENC-94/95: the shipped control value, pinned literally for
        // the same reason as the model slugs — a silent edit here
        // changes every style call's latency profile. Effort LOW
        // because the alias's current model (Grok 4.7) does not honor
        // "none" (Sean's ruling, ENC-94).
        assertEquals("low", OpenRouterPayload.REASONING_EFFORT)
    }

    @Test
    fun `a request carries no reasoning control by default`() {
        // ENC-96: the primary is a non-reasoning model, so the bare
        // builder shape — what the chain's primary attempt sends —
        // carries no reasoning block at all.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            providerOrder = OpenRouterPayload.PRIMARY_PROVIDER_ORDER,
        )

        assertFalse(request.contains("\"reasoning\""))
    }

    @Test
    fun `a request carries the reasoning control when an effort is given`() {
        // The opt-in a future reasoning-model link would use (ENC-94/95).
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.GROK_MODEL,
            system = system,
            user = user,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )

        assertTrue(request.contains("\"reasoning\":{\"effort\":\"low\"}"))
    }

    @Test
    fun `the reasoning block never combines effort with a token cap`() {
        // ENC-95's regression, pinned: OpenRouter's unified reasoning
        // parameter accepts effort OR max_tokens, not both — the
        // combined ENC-94 shape is what providers rejected with
        // 400-class errors on device. Whatever else changes, the two
        // must never ride together again.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.GROK_MODEL,
            system = system,
            user = user,
            stream = true,
            reasoningEffort = OpenRouterPayload.REASONING_EFFORT,
        )

        assertFalse(request.contains("\"reasoning\":{\"effort\":\"low\",\"max_tokens\""))
        assertFalse(request.contains(",\"max_tokens\":512}"))
    }

    @Test
    fun `a degraded request drops the reasoning block and stream options but still streams`() {
        // ENC-95: the degraded retry's payload shape — the bare
        // streamed request, with nothing optional left to reject.
        val request = OpenRouterPayload.buildChatRequest(
            model = OpenRouterPayload.PRIMARY_MODEL,
            system = system,
            user = user,
            stream = true,
            reasoningEffort = null,
            includeUsage = false,
        )

        assertTrue(request.contains("\"stream\":true"))
        assertFalse(request.contains("\"stream_options\""))
        assertFalse(request.contains("\"reasoning\""))
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
