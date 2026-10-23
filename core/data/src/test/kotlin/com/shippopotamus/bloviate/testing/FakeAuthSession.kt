package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-written fake of [AuthSessionPort] for data-layer tests; publish
 * gate states directly. Uids follow the published state, mirroring the
 * real session's non-anonymous rule.
 */
class FakeAuthSession(
    initialState: AuthState = AuthState.Unauthenticated,
) : AuthSessionPort {

    private val mutableState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableState.asStateFlow()

    override fun currentUid(): String? =
        (mutableState.value as? AuthState.Authenticated)?.user?.uid

    override suspend fun ensureSignedIn(): String? = currentUid()

    override suspend fun signInWithGoogle(): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("FakeAuthSession never signs in"))

    override suspend fun signOut(): Result<Unit> {
        publish(AuthState.Unauthenticated)
        return Result.success(Unit)
    }

    /** Publishes [state] as the current gate state. */
    fun publish(state: AuthState) {
        mutableState.value = state
    }

    companion object {

        /** An authenticated state for [uid]. */
        fun authenticated(uid: String): AuthState.Authenticated =
            AuthState.Authenticated(
                UserProfile(uid = uid, displayName = null, email = null, photoUrl = null),
            )
    }
}
