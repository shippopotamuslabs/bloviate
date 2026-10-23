package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationFailure
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationPort
import javax.inject.Inject

/**
 * The character cap for a generation description (BLO-160 S7), mirroring
 * the server's `MAX_GENERATE_DESCRIPTION_CHARS` (BLO-159 S6): the server
 * normalizes (control-strip, whitespace-collapse) before measuring, so a
 * description exactly at this cap always passes both gates, while one just
 * over it fails here without a network call.
 */
const val MAX_GENERATE_DESCRIPTION_CHARS: Int = 500

/**
 * Generates a custom-style draft from a short description (BLO-160 S7).
 * Trims and validates client-side — a blank or over-cap description fails
 * with [CustomStyleGenerationFailure.InvalidInput] without a port call —
 * and otherwise delegates to [CustomStyleGenerationPort].
 */
class GenerateCustomStyleUseCase @Inject constructor(
    private val generation: CustomStyleGenerationPort,
) {

    /**
     * Generates a draft for [description]. The trimmed text is what ships:
     * leading/trailing whitespace never reaches the server, and the blank
     * and over-cap gates run on that trimmed text.
     */
    suspend operator fun invoke(description: String): CustomStyleGenerationOutcome {
        val trimmed = description.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_GENERATE_DESCRIPTION_CHARS) {
            return CustomStyleGenerationOutcome.Failed(CustomStyleGenerationFailure.InvalidInput)
        }
        return generation.generate(trimmed)
    }
}
