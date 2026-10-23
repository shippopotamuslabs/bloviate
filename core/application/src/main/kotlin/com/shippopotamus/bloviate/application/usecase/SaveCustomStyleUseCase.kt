package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import javax.inject.Inject

/**
 * Saves a custom style (ENC-199). [record]'s definition is validated
 * first; an invalid definition is never stored. Mirrors the dictionary
 * write convention (`AddDictionaryWordUseCase` reports the outcome, the
 * store only changes on success): the returned violations are the
 * outcome, and an empty list means the record was saved.
 */
class SaveCustomStyleUseCase @Inject constructor(
    private val customs: CustomStyleStorePort,
    private val validator: CustomStyleValidator,
) {

    /** Validates [record]'s definition, saves it when valid, and returns every violation (empty on success). */
    suspend operator fun invoke(record: CustomStyleRecord): List<CustomStyleViolation> {
        val violations = validator.validate(record.definition)
        if (violations.isEmpty()) {
            customs.save(record)
        }
        return violations
    }
}
