package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The signed-in Google user behind the app gate (ENC-197). Uids here
 * are Google identities, never anonymous sessions: there is no signup
 * wall beyond the launcher sign-in screen, and anonymous Firebase
 * sessions are not created anymore.
 */
data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

/** Whether the launcher gate shows the sign-in screen or the app shell (ENC-197). */
sealed interface AuthState {

    /** The cached session has not been read yet; the gate waits. */
    data object Loading : AuthState

    /** No non-anonymous Firebase user: the launcher shows the sign-in screen. */
    data object Unauthenticated : AuthState

    /** A Google-signed-in user: the launcher shows the app shell. */
    data class Authenticated(val user: UserProfile) : AuthState
}

/**
 * Google session (ENC-197, Credential Manager + Firebase). Anonymous
 * Firebase users count as signed OUT everywhere here: [currentUid] and
 * [ensureSignedIn] return only a non-anonymous uid, or null. No method
 * on this port ever creates an anonymous session.
 */
interface AuthSessionPort {

    /** Live gate state, driven by Firebase's auth-state listener. */
    val authState: StateFlow<AuthState>

    /** The current non-anonymous uid, or null when not signed in. */
    fun currentUid(): String?

    /**
     * Returns the current non-anonymous uid, or null when signed out.
     * Unlike the ENC-151 contract, this never signs in — it is a read
     * callers use to decide whether style calls can be identified.
     */
    suspend fun ensureSignedIn(): String?

    /** Credential Manager Google flow, linked over any anonymous user. */
    suspend fun signInWithGoogle(): Result<UserProfile>

    /** Firebase sign-out plus Credential Manager state clear. */
    suspend fun signOut(): Result<Unit>
}
