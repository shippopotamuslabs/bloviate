package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportReason
import com.shippopotamus.bloviate.application.port.OutputReportRequest
import com.shippopotamus.bloviate.testing.FakeAuthSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirebaseOutputReportClientTest {

    private class FakeOutputReportGateway(
        var result: Any? = mapOf("reportId" to "report-doc-1", "duplicate" to false),
        var failure: Exception? = null,
    ) : OutputReportGateway {
        var lastReport: Map<String, Any?>? = null
            private set
        var callCount: Int = 0
            private set

        override suspend fun callSubmitOutputReport(report: Map<String, Any?>): Any? {
            callCount++
            lastReport = report
            failure?.let { throw it }
            return result
        }
    }

    private val signedIn = FakeAuthSession(initialState = FakeAuthSession.authenticated(uid = "uid-1"))

    private fun request(provider: String? = null, model: String? = null): OutputReportRequest =
        OutputReportRequest(
            clientReportId = "report-uuid-1",
            styledOutput = "The styled words.",
            sourceTranscript = "the dictated words",
            reason = OutputReportReason.HATEFUL,
            styleId = "seneca",
            provider = provider,
            model = model,
            appVersion = "1.0 (42)",
        )

    @Test
    fun `a signed-out user gets SignedOut without a gateway call`() = runTest {
        val gateway = FakeOutputReportGateway()
        val client = FirebaseOutputReportClient(gateway = gateway, auth = FakeAuthSession())

        assertEquals(OutputReportOutcome.SignedOut, client.submit(request()))
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `the payload carries the exact contract keys with null provenance present`() = runTest {
        val gateway = FakeOutputReportGateway()
        val client = FirebaseOutputReportClient(gateway = gateway, auth = signedIn)

        val outcome = client.submit(request())

        assertEquals(OutputReportOutcome.Submitted(duplicate = false), outcome)
        val sent = gateway.lastReport ?: error("report never sent")
        assertEquals(
            setOf(
                "clientReportId", "styledOutput", "sourceTranscript", "reason",
                "styleId", "provider", "model", "appVersion",
            ),
            sent.keys,
        )
        assertEquals("report-uuid-1", sent["clientReportId"])
        assertEquals("The styled words.", sent["styledOutput"])
        assertEquals("the dictated words", sent["sourceTranscript"])
        assertEquals("hateful", sent["reason"])
        assertEquals("seneca", sent["styleId"])
        assertTrue(sent.containsKey("provider"))
        assertEquals(null, sent["provider"])
        assertTrue(sent.containsKey("model"))
        assertEquals(null, sent["model"])
        assertEquals("1.0 (42)", sent["appVersion"])
    }

    @Test
    fun `known provenance rides into the payload`() = runTest {
        val gateway = FakeOutputReportGateway()
        val client = FirebaseOutputReportClient(gateway = gateway, auth = signedIn)

        client.submit(request(provider = "provider-y", model = "model-x"))

        assertEquals("provider-y", gateway.lastReport?.get("provider"))
        assertEquals("model-x", gateway.lastReport?.get("model"))
    }

    @Test
    fun `a duplicate answer maps to Submitted duplicate`() = runTest {
        val gateway = FakeOutputReportGateway(
            result = mapOf("reportId" to "report-doc-1", "duplicate" to true),
        )
        val client = FirebaseOutputReportClient(gateway = gateway, auth = signedIn)

        assertEquals(OutputReportOutcome.Submitted(duplicate = true), client.submit(request()))
    }

    @Test
    fun `unauthenticated maps to SignedOut and other codes to Unavailable`() = runTest {
        val unauthenticated = FirebaseOutputReportClient(
            gateway = FakeOutputReportGateway(failure = StyleFunctionException(code = "unauthenticated")),
            auth = signedIn,
        )
        assertEquals(OutputReportOutcome.SignedOut, unauthenticated.submit(request()))

        val unavailable = FirebaseOutputReportClient(
            gateway = FakeOutputReportGateway(failure = StyleFunctionException(code = "unavailable")),
            auth = signedIn,
        )
        assertEquals(OutputReportOutcome.Unavailable, unavailable.submit(request()))
    }

    @Test
    fun `a misshapen payload is Unavailable, never a crash`() = runTest {
        listOf(null, "nope", mapOf("something" to "else")).forEach { payload ->
            val gateway = FakeOutputReportGateway(result = payload)
            val client = FirebaseOutputReportClient(gateway = gateway, auth = signedIn)

            assertEquals(OutputReportOutcome.Unavailable, client.submit(request()), "payload: $payload")
        }
    }
}
