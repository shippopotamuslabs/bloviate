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
