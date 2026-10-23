package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthStateMappingTest {

    @Test
    fun `non-anonymous user maps to authenticated with its profile`() {
        assertEquals(
            AuthState.Authenticated(
                user = UserProfile(
                    uid = "uid-1",
                    displayName = "Sean",
                    email = "sean@example.com",
                    photoUrl = null,
                ),
            ),
            toAuthState(
                uid = "uid-1",
                displayName = "Sean",
                email = "sean@example.com",
                photoUrl = null,
                isAnonymous = false,
            ),
        )
    }

    @Test
    fun `anonymous user maps to unauthenticated - it never opens the gate`() {
        assertEquals(
            AuthState.Unauthenticated,
            toAuthState(
                uid = "uid-1",
                displayName = null,
                email = null,
                photoUrl = null,
                isAnonymous = true,
            ),
        )
    }

    @Test
    fun `no user maps to unauthenticated`() {
        assertEquals(
            AuthState.Unauthenticated,
            toAuthState(
                uid = null,
                displayName = null,
                email = null,
                photoUrl = null,
                isAnonymous = false,
            ),
        )
    }
}
