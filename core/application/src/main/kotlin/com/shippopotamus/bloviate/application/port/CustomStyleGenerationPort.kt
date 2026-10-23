package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.FewShot

/**
 * Why a custom-style generation call failed (BLO-160 S7). Every failure is
 * a terminal outcome, never a throw: like [CustomStyleSyncFailure], a dead
 * network or a missing entitlement must never crash the caller — the Start
 * screen simply reports it and the draft stays untouched.
 */
enum class CustomStyleGenerationFailure {
    /** No signed-in Google user; the caller never reached the network. */
    SignedOut,

    /** Signed in, but not entitled (non-Google identity or no styles entitlement). */
    NotEntitled,

    /** The classifier read the description and refused it as not a style description. */
    NotAStyleDescription,

    /** The model output (or its single repair) failed draft validation. */
    GenerationInvalid,

    /** The classifier verdict could not be parsed; retryable. */
    ClassifierUnavailable,

    /** The account exhausted its generation caps for the period; retryable later. */
    QuotaReached,

    /**
     * The description failed client-side validation (blank or over the
     * character cap). Only [GenerateCustomStyleUseCase] emits this — port
     * implementations treat every description they receive as valid, since
     * the use case gates first.
     */
    InvalidInput,

    /** Transport trouble, a server-side outage, or a malformed draft payload; retryable. */
    Unavailable,
}

/**
 * One generated custom-style draft (BLO-160 S7): the server-validated
 * voice the Start screen reviews in the shared editor. Carries no id, kind,
 * category, or revision — the save path derives those, so a draft is
 * create-only by construction. The few-shot raw sides are the server's
 * canonical dictations; the styled sides are the model's voice.
 */
data class GeneratedStyleDraft(
    val displayName: String,
    val description: String,
    val sample: String,
    val systemInstructions: String,
    val fewShots: List<FewShot>,
)

/**
 * The outcome of one custom-style generation call (BLO-160 S7), mirroring
 * the [CustomStyleSyncOutcome] precedent: success carries the draft,
 * failure carries a typed [CustomStyleGenerationFailure].
 */
sealed interface CustomStyleGenerationOutcome {

    /** The server returned a validated [draft]. */
    data class Ok(val draft: GeneratedStyleDraft) : CustomStyleGenerationOutcome

    /** The call did not reach a usable draft; [failure] says why. */
    data class Failed(val failure: CustomStyleGenerationFailure) : CustomStyleGenerationOutcome
}

/**
 * The server side of AI style-draft generation (BLO-160 S7): the
 * `generateCustomStyle` callable (BLO-159 S6) behind an application port.
 * Implementations ensure a signed-in user first and map every transport,
 * classifier, validation, or payload problem onto
 * [CustomStyleGenerationOutcome.Failed], never a throw (except coroutine
 * cancellation, which is rethrown).
 */
interface CustomStyleGenerationPort {

    /**
     * Generates a validated draft from [description]. [description] arrives
     * trimmed and within the character cap — the use case gates that, so
     * implementations send it as-is.
     */
    suspend fun generate(description: String): CustomStyleGenerationOutcome
}
