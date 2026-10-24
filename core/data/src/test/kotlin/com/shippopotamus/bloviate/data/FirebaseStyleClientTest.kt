package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.LlmStyleOutcome
import com.shippopotamus.bloviate.application.port.StyleTelemetryPort
import com.shippopotamus.bloviate.application.port.UserProfile
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private class FakeStyleTelemetryPort(
        var failure: Exception? = null,
    ) : StyleTelemetryPort {
        data class Record(
            val styleId: String,
            val styled: Boolean,
            val reason: String?,
            val cause: Throwable?,
        )

        val records = mutableListOf<Record>()

        override fun recordStyleOutcome(styleId: String, styled: Boolean, reason: String?, cause: Throwable?) {
            failure?.let { throw it }
            records += Record(styleId = styleId, styled = styled, reason = reason, cause = cause)
        }
    }

    private class FakeAuthSession(
        var uid: String? = "uid-1",
    ) : AuthSessionPort {
        var ensureCallCount: Int = 0
            private set

        override val authState: StateFlow<AuthState> =
            MutableStateFlow(
                if (uid != null) {
                    AuthState.Authenticated(UserProfile(uid = uid!!, displayName = null, email = null, photoUrl = null))
                } else {
                    AuthState.Unauthenticated
                },
            ).asStateFlow()

        override fun currentUid(): String? = uid

        override suspend fun ensureSignedIn(): String? {
            ensureCallCount++
            return uid
        }

        override suspend fun signInWithGoogle(): Result<UserProfile> =
            Result.failure(UnsupportedOperationException("FakeAuthSession never signs in"))

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `blank text returns Styled without gateway or auth calls`() = runTest {
        val gateway = FakeStyleFunctionGateway()
        val auth = FakeAuthSession()
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, telemetry = FakeStyleTelemetryPort(), clock = { 100L })

        val outcome = client.style(professional, "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertEquals(0, gateway.callCount)
        assertEquals(0, auth.ensureCallCount)
    }

    @Test
    fun `no auth falls back without gateway call`() = runTest {
        val gateway = FakeStyleFunctionGateway()
        val auth = FakeAuthSession(uid = null)
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, telemetry = FakeStyleTelemetryPort(), clock = { 100L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-auth"), outcome)
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `success returns Styled with gateway text`() = runTest {
        val gateway = FakeStyleFunctionGateway(result = "Polished words.")
        val auth = FakeAuthSession()
        val client = FirebaseStyleClient(gateway = gateway, auth = auth, telemetry = FakeStyleTelemetryPort(), clock = { 1_000L })

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
        val client = FirebaseStyleClient(gateway = gateway, auth = FakeAuthSession(), telemetry = FakeStyleTelemetryPort(), clock = { 0L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "permission-denied"), outcome)
    }

    @Test
    fun `generic exception maps to transport-error`() = runTest {
        val gateway = FakeStyleFunctionGateway(failure = IllegalStateException("boom"))
        val client = FirebaseStyleClient(gateway = gateway, auth = FakeAuthSession(), telemetry = FakeStyleTelemetryPort(), clock = { 0L })

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "transport-error"), outcome)
    }

    @Test
    fun `success records styled outcome exactly once`() = runTest {
        val telemetry = FakeStyleTelemetryPort()
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(result = "Polished words."),
            auth = FakeAuthSession(),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertTrue(outcome is LlmStyleOutcome.Styled)
        assertEquals(1, telemetry.records.size)
        val record = telemetry.records.single()
        assertEquals("professional", record.styleId)
        assertEquals(true, record.styled)
        assertEquals(null, record.reason)
    }

    @Test
    fun `no-auth records fell-back without gateway call`() = runTest {
        val gateway = FakeStyleFunctionGateway()
        val telemetry = FakeStyleTelemetryPort()
        val client = FirebaseStyleClient(
            gateway = gateway,
            auth = FakeAuthSession(uid = null),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-auth"), outcome)
        assertEquals(0, gateway.callCount)
        assertEquals(1, telemetry.records.size)
        val record = telemetry.records.single()
        assertEquals("professional", record.styleId)
        assertEquals(false, record.styled)
        assertEquals("no-auth", record.reason)
    }

    @Test
    fun `StyleFunctionException records its code with the cause`() = runTest {
        val telemetry = FakeStyleTelemetryPort()
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(failure = StyleFunctionException(code = "permission-denied")),
            auth = FakeAuthSession(),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "permission-denied"), outcome)
        assertEquals(1, telemetry.records.size)
        val record = telemetry.records.single()
        assertEquals(false, record.styled)
        assertEquals("permission-denied", record.reason)
        assertTrue(record.cause is StyleFunctionException)
    }

    @Test
    fun `transport-error records fell-back with the cause`() = runTest {
        val telemetry = FakeStyleTelemetryPort()
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(failure = IllegalStateException("boom")),
            auth = FakeAuthSession(),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "transport-error"), outcome)
        assertEquals(1, telemetry.records.size)
        val record = telemetry.records.single()
        assertEquals(false, record.styled)
        assertEquals("transport-error", record.reason)
        assertTrue(record.cause is IllegalStateException)
    }

    @Test
    fun `blank text records nothing`() = runTest {
        val telemetry = FakeStyleTelemetryPort()
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(),
            auth = FakeAuthSession(),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "   ")

        assertEquals(LlmStyleOutcome.Styled(text = "   ", llmMs = 0L), outcome)
        assertTrue(telemetry.records.isEmpty())
    }

    @Test
    fun `throwing telemetry never changes the styled outcome`() = runTest {
        val telemetry = FakeStyleTelemetryPort(failure = IllegalStateException("telemetry down"))
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(result = "Polished words."),
            auth = FakeAuthSession(),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertTrue(outcome is LlmStyleOutcome.Styled)
        assertEquals("Polished words.", (outcome as LlmStyleOutcome.Styled).text)
    }

    @Test
    fun `throwing telemetry never changes the fell-back outcome`() = runTest {
        val telemetry = FakeStyleTelemetryPort(failure = IllegalStateException("telemetry down"))
        val client = FirebaseStyleClient(
            gateway = FakeStyleFunctionGateway(),
            auth = FakeAuthSession(uid = null),
            telemetry = telemetry,
            clock = { 0L },
        )

        val outcome = client.style(professional, "raw words")

        assertEquals(LlmStyleOutcome.FellBack(reason = "no-auth"), outcome)
    }
}
