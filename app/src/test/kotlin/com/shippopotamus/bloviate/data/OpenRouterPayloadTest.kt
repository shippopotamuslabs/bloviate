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
    fun `the style chain is grok latest then haiku 5 5 then local`() {
        // ENC-84 (Sean, 2026-10-19): the primary is the floating alias —
        // pinning the literal strings here is what makes an accidental
        // slug edit a test failure instead of a silent behavior change.
        assertEquals("~x-ai/grok-latest", OpenRouterPayload.PRIMARY_MODEL)
        assertEquals("anthropic/claude-haiku-5.5", OpenRouterPayload.FALLBACK_MODEL)
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
    fun `assistant content is extracted from a choices envelope`() {
        val body = "{\"id\":\"x\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Polished text.\"}}]}"

        assertEquals("Polished text.", OpenRouterPayload.extractAssistantContent(body))
    }

    @Test
    fun `escaped sequences in the answer are unescaped`() {
        val body = "{\"choices\":[{\"message\":{\"content\":\"Line one.\\nLine \\\"two\\\".\"}}]}"

        assertEquals("Line one.\nLine \"two\".", OpenRouterPayload.extractAssistantContent(body))
    }

    @Test
    fun `a content decoy inside an error string does not resolve as the answer`() {
        val body = "{\"error\":{\"message\":\"the \\\"content\\\" field is missing\"}}"

        assertNull(OpenRouterPayload.extractAssistantContent(body))
    }

    @Test
    fun `missing choices read as no answer`() {
        assertNull(OpenRouterPayload.extractAssistantContent("{\"error\":{\"code\":500}}"))
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
