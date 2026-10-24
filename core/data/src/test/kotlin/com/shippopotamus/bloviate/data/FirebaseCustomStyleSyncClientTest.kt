package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import com.shippopotamus.bloviate.testing.FakeAuthSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FirebaseCustomStyleSyncClientTest {

    private class FakeCustomStyleSyncGateway(
        var result: Any? = null,
        var failure: Exception? = null,
    ) : CustomStyleSyncGateway {
        var listCallCount: Int = 0
            private set
        var lastSaveDefinition: Map<String, Any?>? = null
            private set
        var lastSaveRevision: Long? = null
            private set
        var lastDeleteId: String? = null
            private set

        override suspend fun callListCustomStyles(): Any? {
            listCallCount++
            failure?.let { throw it }
            return result
        }

        override suspend fun callSaveCustomStyle(
            definition: Map<String, Any?>,
            expectedRevision: Long?,
        ): Any? {
            lastSaveDefinition = definition
            lastSaveRevision = expectedRevision
            failure?.let { throw it }
            return result
        }

        override suspend fun callDeleteCustomStyle(id: String): Any? {
            lastDeleteId = id
            failure?.let { throw it }
            return result
        }
    }

    private val signedIn = FakeAuthSession(initialState = FakeAuthSession.authenticated(uid = "uid-1"))

    private fun canonicalStyle(id: String = "custom-mine"): Map<String, Any?> =
        mapOf(
            "id" to id,
            "kind" to "llm",
            "category" to "custom",
            "displayName" to "Mine",
            "description" to "A custom style.",
            "sample" to "Yeah, tomorrow.",
            "systemInstructions" to "My voice.",
            "fewShots" to listOf(
                mapOf("raw" to "hear this", "styled" to "Hear this."),
                mapOf("raw" to "try again", "styled" to "Try again."),
            ),
            "revision" to 2L,
            "createdAtEpochMillis" to 10L,
            "updatedAtEpochMillis" to 20L,
        )

    private fun createDefinition(): StyleDefinition =
        StyleDefinition(
            id = "",
            displayName = "Mine",
            description = "A custom style.",
            sample = "Yeah, tomorrow.",
            systemInstructions = "My voice.",
            fewShots = listOf(
                com.shippopotamus.bloviate.domain.model.FewShot(raw = "hear this", styled = "Hear this."),
                com.shippopotamus.bloviate.domain.model.FewShot(raw = "try again", styled = "Try again."),
            ),
            kind = StyleKind.LLM,
            category = StyleCategory.CUSTOM,
        )

    @Test
    fun `a signed-out list fails without a gateway call`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("styles" to emptyList<Any>()))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = FakeAuthSession())

        assertEquals(
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut),
            client.list(),
        )
        assertEquals(0, gateway.listCallCount)
    }

    @Test
    fun `a list parses the canonical styles`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(
            result = mapOf("styles" to listOf(canonicalStyle(), canonicalStyle(id = "custom-other")), "syncedAtEpochMillis" to 99L),
        )
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        val outcome = client.list() as CustomStyleSyncOutcome.Ok

        assertEquals(listOf("custom-mine", "custom-other"), outcome.value.map { record -> record.definition.id })
        val mine = outcome.value.first()
        assertEquals(2L, mine.revision)
        assertEquals(20L, mine.updatedAtEpochMillis)
        assertEquals(StyleKind.LLM, mine.definition.kind)
        assertEquals(StyleCategory.CUSTOM, mine.definition.category)
        assertEquals(listOf("hear this" to "Hear this.", "try again" to "Try again."), mine.definition.fewShots.map { it.raw to it.styled })
    }

    @Test
    fun `a list drops malformed entries while its siblings survive`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(
            result = mapOf(
                "styles" to listOf(
                    canonicalStyle(),
                    mapOf("id" to "", "kind" to "llm"),
                    "not-a-map",
                    mapOf("id" to "custom-bad-kind", "kind" to "not-a-kind", "category" to "custom"),
                ),
            ),
        )
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        val outcome = client.list() as CustomStyleSyncOutcome.Ok

        assertEquals(listOf("custom-mine"), outcome.value.map { record -> record.definition.id })
    }

    @Test
    fun `a misshapen list envelope fails without crashing`() = runTest {
        listOf(null, "styles", mapOf("styles" to "nope"), mapOf<String, Any?>()).forEach { payload ->
            val gateway = FakeCustomStyleSyncGateway(result = payload)
            val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

            assertEquals(
                CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable),
                client.list(),
                "payload: $payload",
            )
        }
    }

    @ParameterizedTest(name = "callable code {0} maps to {1}")
    @CsvSource(
        "unauthenticated, SignedOut",
        "permission-denied, NotEntitled",
        "invalid-argument, Invalid",
        "failed-precondition, Conflict",
        "resource-exhausted, LimitReached",
        "unavailable, Unavailable",
        "internal, Unavailable",
        "transport-error, Unavailable",
    )
    fun `callable codes map onto sync failures`(code: String, failure: CustomStyleSyncFailure) = runTest {
        val gateway = FakeCustomStyleSyncGateway(failure = StyleFunctionException(code = code))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(CustomStyleSyncOutcome.Failed(failure), client.list())
    }

    @Test
    fun `a generic exception maps to Unavailable`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(failure = IllegalStateException("boom"))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable), client.list())
    }

    @Test
    fun `a create sends the definition without an id and parses the returned style`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("style" to canonicalStyle(id = "custom-allocated")))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        val outcome = client.save(createDefinition(), expectedRevision = null) as CustomStyleSyncOutcome.Ok

        assertEquals("custom-allocated", outcome.value.definition.id)
        assertEquals(2L, outcome.value.revision)
        val sent = gateway.lastSaveDefinition ?: error("save never sent a definition")
        assertTrue(!sent.containsKey("id"))
        assertEquals("Mine", sent["displayName"])
        assertEquals("My voice.", sent["systemInstructions"])
        assertEquals(
            listOf(mapOf("raw" to "hear this", "styled" to "Hear this."), mapOf("raw" to "try again", "styled" to "Try again.")),
            sent["fewShots"],
        )
    }

    @Test
    fun `an update sends the id with the expected revision`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("style" to canonicalStyle()))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        client.save(createDefinition().copy(id = "custom-mine"), expectedRevision = 2L)

        assertEquals("custom-mine", gateway.lastSaveDefinition?.get("id"))
        assertEquals(2L, gateway.lastSaveRevision)
    }

    @Test
    fun `a malformed save payload fails without crashing`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("style" to mapOf("id" to "custom-x")))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable),
            client.save(createDefinition(), expectedRevision = null),
        )
    }

    @Test
    fun `a signed-out save fails without a gateway call`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("style" to canonicalStyle()))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = FakeAuthSession())

        assertEquals(
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.SignedOut),
            client.save(createDefinition(), expectedRevision = null),
        )
        assertEquals(null, gateway.lastSaveDefinition)
    }

    @Test
    fun `a delete returns the server boolean`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("deleted" to true))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(CustomStyleSyncOutcome.Ok(true), client.delete("custom-mine"))
        assertEquals("custom-mine", gateway.lastDeleteId)
    }

    @Test
    fun `an idempotent server delete still succeeds with false`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("deleted" to false))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(CustomStyleSyncOutcome.Ok(false), client.delete("custom-ghost"))
    }

    @Test
    fun `a malformed delete payload fails without crashing`() = runTest {
        val gateway = FakeCustomStyleSyncGateway(result = mapOf("deleted" to "yes"))
        val client = FirebaseCustomStyleSyncClient(gateway = gateway, auth = signedIn)

        assertEquals(
            CustomStyleSyncOutcome.Failed(CustomStyleSyncFailure.Unavailable),
            client.delete("custom-mine"),
        )
    }
}
