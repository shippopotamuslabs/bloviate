package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.LlmStyleOutcome
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseStyleClientTest {

    private val professional = StyleCatalog.byId("professional")

    private class FakeStyleFunctionGateway(
        var result: String = "Styled text.",
        var failure: Exception? = null,
    ) : StyleFunctionGateway {
        var callCount: Int = 0
            private set
        var lastStyleId: String? = null
            private set
        var lastTranscript: String? = null
            private set

        override suspend fun callStyleText(styleId: String, transcript: String): String {
            callCount++
            lastStyleId = styleId
            lastTranscript = transcript
            failure?.let { throw it }
            return result
        }
    }

    private class FakeAuthSession(
        var uid: String? = "uid-1",
    ) : AuthSessionPort {
        var ensureCallCount: Int = 0
            private set

        override fun currentUid(): String? = uid

        override suspend fun ensureSignedIn(): String? {
            ensureCallCount++
            return uid
        }
    }

    @Test
    fun `blank text returns Styled without gateway or auth calls`() = runTest {
        val gateway = FakeStyleFunctionGateway()
        val auth = FakeAuthSession()
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, clock = { 100L })

        val outcome = client.style(professional, "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertEquals(0, gateway.callCount)
        assertEquals(0, auth.ensureCallCount)
    }

    @Test
    fun `no auth falls back without gateway call`() = runTest {
        val gateway = FakeStyleFunctionGateway()
        val auth = FakeAuthSession(uid = null)
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, clock = { 100L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-auth"), outcome)
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `success returns Styled with gateway text`() = runTest {
        val gateway = FakeStyleFunctionGateway(result = "Polished words.")
        val auth = FakeAuthSession()
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, clock = { 1_000L })

        val outcome = client.style(professional, "raw words")

        assertTrue(outcome is LlmStyleOutcome.Styled)
        val styled = outcome as LlmStyleOutcome.Styled
        assertEquals("Polished words.", styled.text)
        assertEquals("professional", gateway.lastStyleId)
        assertEquals("raw words", gateway.lastTranscript)
        assertEquals(1, gateway.callCount)
    }

    @Test
    fun `StyleFunctionException maps to FellBack with its code`() = runTest {
        val gateway = FakeStyleFunctionGateway(failure = StyleFunctionException(code = "permission-denied"))
        val client = FirebaseStyleClient(gateway = gateway, auth = FakeAuthSession(), clock = { 0L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "permission-denied"), outcome)
    }

    @Test
    fun `generic exception maps to transport-error`() = runTest {
        val gateway = FakeStyleFunctionGateway(failure = IllegalStateException("boom"))
        val client = FirebaseStyleClient(gateway = gateway, auth = FakeAuthSession(), clock = { 0L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "transport-error"), outcome)
    }
}
