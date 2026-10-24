package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile

/**
 * Pure Firebase-user mapping behind the auth gate (ENC-197): kept
 * separate from [GoogleAuthSession] so the rule is unit-testable
 * without a Firebase SDK boundary. Anonymous users count as signed
 * out — the gate never treats them as authenticated.
 */
internal fun toAuthState(
    uid: String?,
    displayName: String?,
    email: String?,
    photoUrl: String?,
    isAnonymous: Boolean,
): AuthState =
    if (uid != null && !isAnonymous) {
        AuthState.Authenticated(
            UserProfile(
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
            ),
        )
    } else {
        AuthState.Unauthenticated
    }
