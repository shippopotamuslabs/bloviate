package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleDraft
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleValidator
import com.shippopotamus.bloviate.domain.evaluator.CustomStyleViolation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateCustomStyleDraftUseCaseTest {

    private val useCase = ValidateCustomStyleDraftUseCase(CustomStyleValidator())

    @Test
    fun `a valid create draft has no violations and no id complaint`() {
        // A create has no id yet; validation runs against the placeholder,
        // so no ID_PREFIX violation can appear for a null styleId.
        assertEquals(emptyList<CustomStyleViolation>(), useCase(validDraft(), styleId = null))
    }

    @Test
    fun `a valid edit draft under its real id has no violations`() {
        assertEquals(emptyList<CustomStyleViolation>(), useCase(validDraft(), styleId = "custom-mine"))
    }

    @Test
    fun `an edit under a non-custom id reports the prefix violation`() {
        val violations = useCase(validDraft(), styleId = "polish")

        assertTrue(violations.contains(CustomStyleViolation.ID_PREFIX))
    }

    @Test
    fun `an edit under a bare prefix reports the blank suffix`() {
        val violations = useCase(validDraft(), styleId = "custom-")

        assertTrue(violations.contains(CustomStyleViolation.ID_SUFFIX_BLANK))
    }

    @Test
    fun `a blank draft reports every blank field and pair side`() {
        val violations = useCase(
            CustomStyleDraft(
                displayName = "",
                description = "",
                sample = "",
                systemInstructions = "",
                fewShotPairs = listOf("" to "", "" to ""),
            ),
            styleId = null,
        )

        assertTrue(violations.contains(CustomStyleViolation.DISPLAY_NAME_BLANK))
        assertTrue(violations.contains(CustomStyleViolation.DESCRIPTION_BLANK))
        assertTrue(violations.contains(CustomStyleViolation.SAMPLE_BLANK))
        assertTrue(violations.contains(CustomStyleViolation.INSTRUCTIONS_BLANK))
        assertTrue(violations.contains(CustomStyleViolation.FEWSHOT_RAW_BLANK))
        assertTrue(violations.contains(CustomStyleViolation.FEWSHOT_STYLED_BLANK))
    }

    @Test
    fun `an over-cap name reports the length violation`() {
        val violations = useCase(
            validDraft().copy(displayName = "n".repeat(41)),
            styleId = null,
        )

        assertEquals(listOf(CustomStyleViolation.DISPLAY_NAME_TOO_LONG), violations)
    }

    @Test
    fun `pair counts outside two to five report the count violations`() {
        val onePair = useCase(validDraft().copy(fewShotPairs = listOf("a" to "A.")), styleId = null)
        assertTrue(onePair.contains(CustomStyleViolation.TOO_FEW_FEWSHOTS))

        val sixPairs = useCase(
            validDraft().copy(fewShotPairs = List(6) { index -> "raw $index" to "Styled $index." }),
            styleId = null,
        )
        assertTrue(sixPairs.contains(CustomStyleViolation.TOO_MANY_FEWSHOTS))
    }

    private fun validDraft(): CustomStyleDraft =
        CustomStyleDraft(
            displayName = "Mine",
            description = "A custom style.",
            sample = "Yeah, tomorrow.",
            systemInstructions = "My voice. NEVER change facts.",
            fewShotPairs = listOf(
                "hear this" to "Hear this.",
                "try again" to "Try again.",
            ),
        )
}
