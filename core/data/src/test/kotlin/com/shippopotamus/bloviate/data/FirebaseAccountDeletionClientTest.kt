package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.testing.FakeAuthSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FirebaseAccountDeletionClientTest {

    private class FakeAccountDeletionGateway(
        var result: Any? = mapOf("deleted" to true),
        var failure: Exception? = null,
    ) : AccountDeletionGateway {
        var callCount: Int = 0
            private set

        override suspend fun callDeleteAccount(): Any? {
            callCount++
            failure?.let { throw it }
            return result
        }
    }

    private val signedIn = FakeAuthSession(initialState = FakeAuthSession.authenticated(uid = "uid-1"))

    @Test
    fun `a signed-out user gets SignedOut without a gateway call`() = runTest {
        val gateway = FakeAccountDeletionGateway()
        val client = FirebaseAccountDeletionClient(gateway = gateway, auth = FakeAuthSession())

        assertEquals(AccountDeletionOutcome.SignedOut, client.deleteAccount())
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun `a successful call is Deleted`() = runTest {
        val gateway = FakeAccountDeletionGateway()
        val client = FirebaseAccountDeletionClient(gateway = gateway, auth = signedIn)

        assertEquals(AccountDeletionOutcome.Deleted, client.deleteAccount())
        assertEquals(1, gateway.callCount)
    }

    @Test
    fun `failed-precondition with the recent-login message is RequiresRecentLogin`() = runTest {
        val gateway = FakeAccountDeletionGateway(
            failure = StyleFunctionException(code = "failed-precondition", message = "requires-recent-login"),
        )
        val client = FirebaseAccountDeletionClient(gateway = gateway, auth = signedIn)

        assertEquals(AccountDeletionOutcome.RequiresRecentLogin, client.deleteAccount())
    }

    @Test
    fun `failed-precondition without a surviving message still maps to RequiresRecentLogin`() = runTest {
        // The deleteAccount callable throws exactly one
        // failed-precondition (the recent-login gate), so the mapping
        // holds even when a transport layer drops the message.
        val gateway = FakeAccountDeletionGateway(
            failure = StyleFunctionException(code = "failed-precondition"),
        )
        val client = FirebaseAccountDeletionClient(gateway = gateway, auth = signedIn)

        assertEquals(AccountDeletionOutcome.RequiresRecentLogin, client.deleteAccount())
    }

    @Test
    fun `other callable codes are Unavailable`() = runTest {
        listOf("unavailable", "internal", "permission-denied", "transport-error").forEach { code ->
            val gateway = FakeAccountDeletionGateway(failure = StyleFunctionException(code = code))
            val client = FirebaseAccountDeletionClient(gateway = gateway, auth = signedIn)

            assertEquals(AccountDeletionOutcome.Unavailable, client.deleteAccount(), "code: $code")
        }
    }

    @Test
    fun `a generic exception is Unavailable`() = runTest {
        val gateway = FakeAccountDeletionGateway(failure = IllegalStateException("boom"))
        val client = FirebaseAccountDeletionClient(gateway = gateway, auth = signedIn)

        assertEquals(AccountDeletionOutcome.Unavailable, client.deleteAccount())
    }
}
