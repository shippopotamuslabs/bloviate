package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleDraft
import com.shippopotamus.bloviate.application.port.forCustomStyleValidation
import com.shippopotamus.bloviate.application.port.toDefinition
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import javax.inject.Inject

/**
 * Validates an editor draft as a custom style (BLO-176): the seam the
 * editor's per-keystroke validation runs through, so presentation never
 * touches the domain [CustomStyleValidator] directly (ARCHITECTURE_RULES
 * 3.4 — ViewModels depend on use cases only). [styleId] is the loaded
 * style's id on an edit and null on a create; a create validates against
 * the placeholder id ([forCustomStyleValidation]) because the server
 * allocates the real one. The returned violations are exactly
 * [SaveSyncedCustomStyleUseCase]'s gate — the same construction and the
 * same validator — so a draft that reads valid here can never read
 * invalid at save.
 */
class ValidateCustomStyleDraftUseCase @Inject constructor(
    private val validator: CustomStyleValidator,
) {

    /** Every rule [draft] breaks as a custom style, in field order; empty means it can save. */
    operator fun invoke(
        draft: CustomStyleDraft,
        styleId: String?,
    ): List<CustomStyleViolation> =
        validator.validate(draft.toDefinition(styleId).forCustomStyleValidation())
}
