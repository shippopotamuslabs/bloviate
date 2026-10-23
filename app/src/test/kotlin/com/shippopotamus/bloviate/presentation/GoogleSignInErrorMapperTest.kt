package com.shippopotamus.bloviate.presentation

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class GoogleSignInErrorMapperTest {

    @Test
    fun `network failure maps to the network message`() {
        assertEquals(MSG_NETWORK, IOException("timeout").toGoogleSignInMessage())
    }

    @Test
    fun `missing credentials maps to the no-account message`() {
        assertEquals(MSG_NO_ACCOUNT, NoCredentialException().toGoogleSignInMessage())
    }

    @Test
    fun `cancellation maps to the cancelled message but counts as a dismissal`() {
        val failure = GetCredentialCancellationException()
        assertEquals(MSG_CANCELLED, failure.toGoogleSignInMessage())
        assertEquals(true, failure.isSignInDismissal())
    }

    @Test
    fun `other throwables are not dismissals`() {
        assertEquals(false, IOException("timeout").isSignInDismissal())
    }

    @Test
    fun `reauth cancellation is not a dismissal and maps to the reauth message`() {
        val failure = GetCredentialCancellationException("[16] Account reauth failed.")
        assertEquals(false, failure.isSignInDismissal())
        assertEquals(MSG_REAUTH, failure.toGoogleSignInMessage())
    }

    @Test
    fun `reauth nested as cause is not a dismissal and maps to the reauth message`() {
        val failure = GetCredentialCancellationException("GetCredential failed").apply {
            initCause(IllegalStateException("[16] Account ReAuth failed."))
        }
        assertEquals(false, failure.isSignInDismissal())
        assertEquals(MSG_REAUTH, failure.toGoogleSignInMessage())
    }

    @Test
    fun `unconfigured build maps to the config message`() {
        assertEquals(
            MSG_CONFIG,
            IllegalStateException("Google Sign-In is not configured in this build.").toGoogleSignInMessage(),
        )
    }

    @Test
    fun `technical failure maps to the generic message`() {
        assertEquals(
            MSG_GENERIC,
            IllegalStateException("com.example.Auth failed with status code 7").toGoogleSignInMessage(),
        )
    }

    @Test
    fun `short human failure passes through`() {
        assertEquals("Please try again.", IllegalStateException("Please try again.").toGoogleSignInMessage())
    }
}
