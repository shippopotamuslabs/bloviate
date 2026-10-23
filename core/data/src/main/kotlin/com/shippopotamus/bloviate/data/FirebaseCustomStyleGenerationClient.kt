package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationPort
import com.shippopotamus.bloviate.application.port.GeneratedStyleDraft
import com.shippopotamus.bloviate.domain.model.FewShot
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [CustomStyleGenerationPort] via the `generateCustomStyle` callable
 * (BLO-159 S6, BLO-160 S7), mirroring [FirebaseCustomStyleSyncClient]:
 * [AuthSessionPort] signs the caller in first (a signed-out user gets
 * [SignedOut] without a network call), every callable or payload failure
 * becomes a typed [CustomStyleGenerationFailure], and only cancellation is
 * rethrown. Logs carry outcome codes only — never the description or draft
 * content.
 *
 * Payload parsing is defensive by design: the wire shape is `Map`s of
 * `Any?`, so a missing key or a wrong type maps onto [Unavailable], never
 * a crash. Reason refinement reads the server's reason message (the
 * `HttpsError` message: `not-a-style-description`,
 * `classifier-verdict-unparseable`, `generated-style-invalid`): the code
 * alone cannot tell a refused description from a malformed request, so an
 * `invalid-argument` or `failed-precondition` without a known reason also
 * maps onto [Unavailable].
 */
class FirebaseCustomStyleGenerationClient @Inject constructor(
    private val gateway: CustomStyleGenerationGateway,
    private val auth: AuthSessionPort,
) : CustomStyleGenerationPort {

    override suspend fun generate(description: String): CustomStyleGenerationOutcome {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "custom-style-generation outcome=signed-out")
            return CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.SignedOut)
        }
        return try {
            val draft = gateway.callGenerateCustomStyle(description)?.toDraft()
                ?: return failed(CustomStyleGenerationFailure.Unavailable)
            Log.d(TAG, "custom-style-generation outcome=ok")
            CustomStyleGenerationOutcome.Ok(draft)
        } catch (exception: StyleFunctionException) {
            failed(exception.toGenerationFailure())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "custom-style-generation outcome=unavailable", exception)
            CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.Unavailable)
        }
    }

    private fun failed(failure: CustomStyleGenerationFailure): CustomStyleGenerationOutcome.Failed {
        Log.d(TAG, "custom-style-generation outcome=$failure")
        return CustomStyleGenerationOutcome.Failed(failure)
    }

    private companion object {
        const val TAG: String = "CustomStyleGeneration"
    }
}

/** The callable error code plus the server's reason message onto a generation failure. */
private fun StyleFunctionException.toGenerationFailure(): CustomStyleGenerationFailure =
    when (code) {
        "unauthenticated" -> CustomStyleGenerationFailure.SignedOut
        "permission-denied" -> CustomStyleGenerationFailure.NotEntitled
        "resource-exhausted" -> CustomStyleGenerationFailure.QuotaReached
        "invalid-argument" ->
            if (message == NOT_STYLE_DESCRIPTION_REASON) {
                CustomStyleGenerationFailure.NotAStyleDescription
            } else {
                CustomStyleGenerationFailure.Unavailable
            }
        "failed-precondition" ->
            when (message) {
                CLASSIFIER_UNPARSEABLE_REASON -> CustomStyleGenerationFailure.ClassifierUnavailable
                GENERATED_DRAFT_INVALID_REASON -> CustomStyleGenerationFailure.GenerationInvalid
                else -> CustomStyleGenerationFailure.Unavailable
            }
        else -> CustomStyleGenerationFailure.Unavailable
    }

/** The `{draft}` envelope decoded, or null when misshapen. */
private fun Any?.toDraft(): GeneratedStyleDraft? {
    val draft = (this as? Map<*, *>)?.get("draft") as? Map<*, *> ?: return null
    val displayName = draft["displayName"] as? String
    val description = draft["description"] as? String
    val sample = draft["sample"] as? String
    val systemInstructions = draft["systemInstructions"] as? String
    if (displayName == null || description == null) return null
    if (sample == null || systemInstructions == null) return null
    val fewShots = (draft["fewShots"] as? List<*>)?.mapNotNull { entry -> entry.toShot() }
    if (fewShots.isNullOrEmpty()) return null
    return GeneratedStyleDraft(
        displayName = displayName,
        description = description,
        sample = sample,
        systemInstructions = systemInstructions,
        fewShots = fewShots,
    )
}

/** One few-shot pair decoded; malformed pairs are skipped, like the sync client's list parsing. */
private fun Any?.toShot(): FewShot? {
    val pair = this as? Map<*, *> ?: return null
    val raw = pair["raw"] as? String ?: return null
    val styled = pair["styled"] as? String ?: return null
    return FewShot(raw = raw, styled = styled)
}

/** Stable machine-readable server reasons (BLO-159 S6 `generateCustomStyle.ts`). */
private const val NOT_STYLE_DESCRIPTION_REASON: String = "not-a-style-description"
private const val CLASSIFIER_UNPARSEABLE_REASON: String = "classifier-verdict-unparseable"
private const val GENERATED_DRAFT_INVALID_REASON: String = "generated-style-invalid"
