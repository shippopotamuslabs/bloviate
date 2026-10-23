package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [AuthSessionPort]; publish gate states and stub results directly. */
class FakeAuthSession(
    initialState: AuthState = AuthState.Unauthenticated,
) : AuthSessionPort {

    private val mutableState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableState.asStateFlow()

    var currentUid: String? =
        (initialState as? AuthState.Authenticated)?.user?.uid
        private set

    var signInResult: Result<UserProfile> =
        Result.failure(IllegalStateException("FakeAuthSession has no sign-in result"))
        private set

    var signOutResult: Result<Unit> = Result.success(Unit)
        private set

    var signOutCallCount: Int = 0
        private set

    /**
     * When set, [signInWithGoogle] suspends on it before answering —
     * lets tests observe the in-flight flag deterministically instead
     * of racing a [StateFlow]'s conflation.
     */
    var signInGate: CompletableDeferred<Unit>? = null

    fun publish(state: AuthState) {
        mutableState.value = state
        currentUid = (state as? AuthState.Authenticated)?.user?.uid
    }

    fun stubSignIn(result: Result<UserProfile>) {
        signInResult = result
    }

    fun stubSignOut(result: Result<Unit>) {
        signOutResult = result
    }

    override fun currentUid(): String? = currentUid

    override suspend fun ensureSignedIn(): String? = currentUid

    override suspend fun signInWithGoogle(): Result<UserProfile> {
        signInGate?.await()
        return signInResult
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult
    }
}
