package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * Why a custom-styles sync call failed (BLO-25 S4). Every failure is a
 * terminal outcome, never a throw: like the send-path
 * [LlmStyleOutcome.FellBack], a dead network or a missing entitlement
 * must never crash the caller — the cache simply stays as it was.
 */
enum class CustomStyleSyncFailure {
    /** No signed-in Google user; the caller never reached the network. */
    SignedOut,

    /** Signed in, but not entitled (non-Google identity or no styles entitlement). */
    NotEntitled,

    /** The definition or id the client sent was rejected by the server. */
    Invalid,

    /** The style changed on the server since [CustomStyleRecord.revision] was read. */
    Conflict,

    /** The account already holds the server's maximum number of custom styles. */
    LimitReached,

    /** Transport trouble or a malformed server payload; retryable. */
    Unavailable,
}

/**
 * The outcome of one custom-styles sync call (BLO-25 S4), mirroring the
 * [LlmStyleOutcome] precedent: success carries the value, failure carries
 * a typed [CustomStyleSyncFailure]. Failures leave the local cache
 * untouched — callers decide what the user sees.
 */
sealed interface CustomStyleSyncOutcome<out T> {

    /** The server answered with [value]. */
    data class Ok<T>(val value: T) : CustomStyleSyncOutcome<T>

    /** The call did not reach a usable answer; [failure] says why. */
    data class Failed(val failure: CustomStyleSyncFailure) : CustomStyleSyncOutcome<Nothing>
}

/**
 * The server side of the account-owned custom-styles cache (BLO-25 S4):
 * the `listCustomStyles` / `saveCustomStyle` / `deleteCustomStyle`
 * callables (BLO-26 S3) behind an application port. Implementations
 * ensure a signed-in user first and map every transport or payload
 * problem onto [CustomStyleSyncOutcome.Failed], never a throw (except
 * coroutine cancellation, which is rethrown).
 */
interface CustomStyleSyncPort {

    /** Lists the account's custom styles; the server's current truth. */
    suspend fun list(): CustomStyleSyncOutcome<List<CustomStyleRecord>>

    /**
     * Saves [definition] on the server. A null [expectedRevision] is a
     * create — the server allocates the id and the returned record
     * carries it; a non-null [expectedRevision] is an update of the
     * existing id, failing with [CustomStyleSyncFailure.Conflict] when
     * the server's revision has moved on.
     */
    suspend fun save(
        definition: StyleDefinition,
        expectedRevision: Long?,
    ): CustomStyleSyncOutcome<CustomStyleRecord>

    /**
     * Deletes [styleId] on the server. Idempotent: deleting an id the
     * server does not hold still succeeds with `false`.
     */
    suspend fun delete(styleId: String): CustomStyleSyncOutcome<Boolean>
}
