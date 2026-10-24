package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStyleSyncFailure
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import javax.inject.Inject

/**
 * The outcome of one synced custom-style save (BLO-25 S4): the saved
 * server record, the S1 validation violations (nothing was sent or
 * stored), or the sync failure (the cache is untouched).
 */
sealed interface SaveSyncedCustomStyleOutcome {

    /** The server saved the style; the cache and (on create) the selection already reflect it. */
    data class Saved(val record: CustomStyleRecord) : SaveSyncedCustomStyleOutcome

    /** [SaveCustomStyleUseCase]'s validation rejected the definition; nothing was sent or stored. */
    data class Invalid(val violations: List<CustomStyleViolation>) : SaveSyncedCustomStyleOutcome

    /** The server call failed; the cache is untouched. */
    data class Failed(val failure: CustomStyleSyncFailure) : SaveSyncedCustomStyleOutcome
}

/**
 * Saves a custom style through the server first, then the account cache
 * (BLO-25 S4). Validation keeps S1's semantics exactly
 * ([CustomStyleValidator]; a blank id reads as a create and validates
 * against a placeholder, since the server allocates the id). The server
 * write lands before any cache write, and the cache write is a
 * [CustomStyleStorePort.replaceSnapshot] of the current snapshot plus
 * the returned record — never [CustomStyleStorePort.save], which is a
 * no-op on a cache the sign-in has not claimed yet (S2 owner semantics).
 * That same replace claims the cache, so the first save after a fresh
 * sign-in sticks. A create also selects the new style; an update never
 * changes the selection.
 */
class SaveSyncedCustomStyleUseCase @Inject constructor(
    private val sync: CustomStyleSyncPort,
    private val customs: CustomStyleStorePort,
    private val selection: StylePreferencePort,
    private val validator: CustomStyleValidator,
) {

    /**
     * Validates [definition], saves it on the server ([expectedRevision]
     * null for a create, the known revision for an update), then merges
     * the returned record into the cache and selects it on create.
     */
    suspend operator fun invoke(
        definition: StyleDefinition,
        expectedRevision: Long?,
    ): SaveSyncedCustomStyleOutcome {
        val violations = validator.validate(definition.forValidation())
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

    /**
     * A create carries no id yet (the server allocates one), but the S1
     * validator requires the `custom-` prefix — so a blank id validates
     * against a placeholder and every other field validates as given.
     */
    private fun StyleDefinition.forValidation(): StyleDefinition =
        if (id.isBlank()) copy(id = CREATE_ID_PLACEHOLDER) else this

    private companion object {
        const val CREATE_ID_PLACEHOLDER: String = "custom-pending"
    }
}
