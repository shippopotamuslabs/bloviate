package com.shippopotamus.bloviate.application.port

/**
 * Anonymous Firebase session (ENC-151). There is no signup wall: the app
 * signs in anonymously so the style Function can identify the caller.
 * [ensureSignedIn] returns the uid, signing in anonymously when needed,
 * or null when sign-in fails (callers then land local text).
 */
interface AuthSessionPort {

    /** The current anonymous uid, or null when not signed in. */
    fun currentUid(): String?

    /** Ensures an anonymous session, returning its uid or null on failure. */
    suspend fun ensureSignedIn(): String?
}
