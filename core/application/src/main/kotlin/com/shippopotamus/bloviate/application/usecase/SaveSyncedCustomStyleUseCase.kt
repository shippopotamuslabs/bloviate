package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleDraft
import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.application.port.forCustomStyleValidation
import com.shippopotamus.bloviate.application.port.toDefinition
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import javax.inject.Inject

/**
 * The outcome of one synced custom-style save (BLO-25 S4): the saved
 * server record, the S1 validation violations (nothing was sent or
 * stored), or the sync failure (the cache is untouched).
 */
sealed interface SaveSyncedCustomStyleOutcome {

    /** The server saved the style; the cache and (on create) the selection already reflect it. */
    data class Saved(val record: CustomStyleRecord) : SaveSyncedCustomStyleOutcome

    /** Validation rejected the draft; nothing was sent or stored. */
    data class Invalid(val violations: List<CustomStyleViolation>) : SaveSyncedCustomStyleOutcome

    /** The server call failed; the cache is untouched. */
    data class Failed(val failure: CustomStyleSyncFailure) : SaveSyncedCustomStyleOutcome
}

/**
 * Saves a custom style through the server first, then the account cache
 * (BLO-25 S4). The caller passes the plain [CustomStyleDraft] (BLO-176);
 * the draft-to-definition construction and validation live here and in
 * [ValidateCustomStyleDraftUseCase], sharing one construction and one
 * placeholder rule ([forCustomStyleValidation]). Validation keeps S1's
 * semantics exactly ([CustomStyleValidator]; a blank id reads as a
 * create and validates against a placeholder, since the server
 * allocates the id). The server write lands before any cache write, and
 * the cache write is a [CustomStyleStorePort.replaceSnapshot] of the
 * current snapshot plus the returned record — never
 * [CustomStyleStorePort.save], which is a no-op on a cache the sign-in
 * has not claimed yet (S2 owner semantics). That same replace claims
 * the cache, so the first save after a fresh sign-in sticks. A create
 * also selects the new style; an update never changes the selection.
 */
class SaveSyncedCustomStyleUseCase @Inject constructor(
    private val sync: CustomStyleSyncPort,
    private val customs: CustomStyleStorePort,
    private val selection: StylePreferencePort,
    private val validator: CustomStyleValidator,
) {

    /**
     * Validates [draft] (built under [styleId]; null is a create, whose
     * id the server allocates), saves it on the server
     * ([expectedRevision] null for a create, the known revision for an
     * update), then merges the returned record into the cache and
     * selects it on create.
     */
    suspend operator fun invoke(
        draft: CustomStyleDraft,
        styleId: String?,
        expectedRevision: Long?,
    ): SaveSyncedCustomStyleOutcome {
        val definition = draft.toDefinition(styleId)
        val violations = validator.validate(definition.forCustomStyleValidation())
        if (violations.isNotEmpty()) {
            return SaveSyncedCustomStyleOutcome.Invalid(violations)
        }
        return when (val outcome = sync.save(definition, expectedRevision)) {
            is CustomStyleSyncOutcome.Ok -> {
                val record = outcome.value
                val merged = customs.state.value.styles
                    .filterNot { existing -> existing.definition.id == record.definition.id } + record
                customs.replaceSnapshot(merged)
                if (expectedRevision == null) {
                    selection.selectStyle(record.definition.id)
                }
                SaveSyncedCustomStyleOutcome.Saved(record)
            }
            is CustomStyleSyncOutcome.Failed ->
                SaveSyncedCustomStyleOutcome.Failed(outcome.failure)
        }
    }
}
