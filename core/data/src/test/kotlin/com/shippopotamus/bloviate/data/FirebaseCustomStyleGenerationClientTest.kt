package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.testing.FakeAuthSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FirebaseCustomStyleGenerationClientTest {

    private class FakeCustomStyleGenerationGateway(
        var result: Any? = null,
        var failure: Exception? = null,
    ) : CustomStyleGenerationGateway {
        var callCount: Int = 0
            private set
        var lastDescription: String? = null
            private set

        override suspend fun callGenerateCustomStyle(description: String): Any? {
            callCount++
            lastDescription = description
            failure?.let { throw it }
            return result
        }
    }

    private val signedIn = FakeAuthSession(initialState = FakeAuthSession.authenticated(uid = "uid-1"))

    private fun canonicalDraft(): Map<String, Any?> =
        mapOf(
            "displayName" to "Noir Detective",
            "description" to "Rain, neon, and regret.",
            "sample" to "The rain kept falling, and so did I.",
            "systemInstructions" to "Short sentences.",
            "fewShots" to listOf(
                mapOf("raw" to "i walked home late", "styled" to "I walked home late."),
                mapOf("raw" to "the meeting is at noon", "styled" to "The meeting is at noon."),
            ),
        )

    @Test
    fun `a signed-out generate fails without a gateway call`() = runTest {
        val gateway = FakeCustomStyleGenerationGateway(result = mapOf("draft" to canonicalDraft()))
        val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = FakeAuthSession())

        assertEquals(
            CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.SignedOut),
            client.generate("writes like noir"),
        )
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `a generate sends exactly the description and parses the draft`() = runTest {
        val gateway = FakeCustomStyleGenerationGateway(result = mapOf("draft" to canonicalDraft()))
        val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = signedIn)

        val outcome = client.generate("writes like noir") as CustomStyleGenerationOutcome.Ok

        assertEquals("writes like noir", gateway.lastDescription)
        assertEquals("Noir Detective", outcome.draft.displayName)
        assertEquals("Rain, neon, and regret.", outcome.draft.description)
        assertEquals("The rain kept falling, and so did I.", outcome.draft.sample)
        assertEquals("Short sentences.", outcome.draft.systemInstructions)
        assertEquals(
            listOf("i walked home late" to "I walked home late.", "the meeting is at noon" to "The meeting is at noon."),
            outcome.draft.fewShots.map { shot -> shot.raw to shot.styled },
        )
    }

    @Test
    fun `a misshapen draft envelope fails without crashing`() = runTest {
        listOf(
            null,
            "draft",
            mapOf<String, Any?>(),
            mapOf("draft" to null),
            mapOf("draft" to "nope"),
            mapOf("draft" to canonicalDraft().minus("displayName")),
            mapOf("draft" to canonicalDraft().minus("fewShots")),
            mapOf("draft" to canonicalDraft() + ("fewShots" to emptyList<Any>())),
            mapOf("draft" to canonicalDraft() + ("fewShots" to listOf(mapOf("raw" to "only-raw")))),
        ).forEach { payload ->
            val gateway = FakeCustomStyleGenerationGateway(result = payload)
            val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = signedIn)

            assertEquals(
                CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.Unavailable),
                client.generate("writes like noir"),
                "payload: $payload",
            )
        }
    }

    @Test
    fun `malformed pairs are skipped while their siblings survive`() = runTest {
        val draft = canonicalDraft() + (
            "fewShots" to listOf(
                mapOf("raw" to "i walked home late", "styled" to "I walked home late."),
                mapOf("raw" to "only-raw"),
                "not-a-map",
            )
            )
        val gateway = FakeCustomStyleGenerationGateway(result = mapOf("draft" to draft))
        val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = signedIn)

        val outcome = client.generate("writes like noir") as CustomStyleGenerationOutcome.Ok

        assertEquals(
            listOf("i walked home late" to "I walked home late."),
            outcome.draft.fewShots.map { shot -> shot.raw to shot.styled },
        )
    }

    @ParameterizedTest(name = "callable {0} with reason {1} maps to {2}")
    @CsvSource(
        "unauthenticated, '', SignedOut",
        "permission-denied, '', NotEntitled",
        "resource-exhausted, '', QuotaReached",
        "invalid-argument, not-a-style-description, NotAStyleDescription",
        "invalid-argument, '', Unavailable",
        "invalid-argument, description must be a non-blank string., Unavailable",
        "failed-precondition, classifier-verdict-unparseable, ClassifierUnavailable",
        "failed-precondition, generated-style-invalid, GenerationInvalid",
        "failed-precondition, OPENROUTER_API_KEY missing., Unavailable",
        "failed-precondition, '', Unavailable",
        "unavailable, style-generation-unavailable, Unavailable",
        "internal, '', Unavailable",
        "transport-error, '', Unavailable",
    )
    fun `callable codes and server reasons map onto generation failures`(
        code: String,
        reason: String,
        failure: CustomStyleGenerationFailure,
    ) = runTest {
        val gateway = FakeCustomStyleGenerationGateway(
            failure = StyleFunctionException(code = code, message = reason.ifEmpty { null }),
        )
        val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = signedIn)

        assertEquals(CustomStyleGenerationOutcome.Failed(failure), client.generate("writes like noir"))
    }

    @Test
    fun `a generic exception maps to Unavailable`() = runTest {
        val gateway = FakeCustomStyleGenerationGateway(failure = IllegalStateException("boom"))
        val client = FirebaseCustomStyleGenerationClient(gateway = gateway, auth = signedIn)

        assertEquals(
            CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.Unavailable),
            client.generate("writes like noir"),
        )
    }
}
