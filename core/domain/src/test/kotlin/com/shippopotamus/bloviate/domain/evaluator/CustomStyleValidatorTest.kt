package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomStyleValidatorTest {

    private val validator = CustomStyleValidator()

    @Test
    fun `a well-formed custom style validates clean`() {
        assertEquals(emptyList<CustomStyleViolation>(), validator.validate(validCustom()))
    }

    @Test
    fun `an id without the custom prefix is rejected`() {
        val violations = validator.validate(validCustom().copy(id = "mine"))
        assertEquals(listOf(CustomStyleViolation.ID_PREFIX), violations)
    }

    @Test
    fun `the bare prefix with a blank suffix is rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(id = "custom-")).contains(CustomStyleViolation.ID_SUFFIX_BLANK),
        )
        assertTrue(
            validator.validate(validCustom().copy(id = "custom-   ")).contains(CustomStyleViolation.ID_SUFFIX_BLANK),
        )
    }

    @Test
    fun `a mechanical style is rejected`() {
        val violations = validator.validate(validCustom().copy(kind = StyleKind.MECHANICAL))
        assertEquals(listOf(CustomStyleViolation.KIND_NOT_LLM), violations)
    }

    @Test
    fun `a non-custom category is rejected`() {
        val violations = validator.validate(validCustom().copy(category = StyleCategory.PERSONAS))
        assertEquals(listOf(CustomStyleViolation.CATEGORY_NOT_CUSTOM), violations)
    }

    @Test
    fun `a blank display name is rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(displayName = "   ")).contains(CustomStyleViolation.DISPLAY_NAME_BLANK),
        )
    }

    @Test
    fun `a display name at exactly forty chars passes and forty-one fails`() {
        assertEquals(emptyList<CustomStyleViolation>(), validator.validate(validCustom().copy(displayName = "n".repeat(40))))
        val violations = validator.validate(validCustom().copy(displayName = "n".repeat(41)))
        assertEquals(listOf(CustomStyleViolation.DISPLAY_NAME_TOO_LONG), violations)
    }

    @Test
    fun `display name length is measured trimmed`() {
        assertEquals(
            emptyList<CustomStyleViolation>(),
            validator.validate(validCustom().copy(displayName = "  " + "n".repeat(40) + "  ")),
        )
    }

    @Test
    fun `a blank description is rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(description = "")).contains(CustomStyleViolation.DESCRIPTION_BLANK),
        )
    }

    @Test
    fun `a description at exactly one hundred twenty chars passes and one more fails`() {
        assertEquals(
            emptyList<CustomStyleViolation>(),
            validator.validate(validCustom().copy(description = "d".repeat(120))),
        )
        assertTrue(
            validator.validate(validCustom().copy(description = "d".repeat(121))).contains(
                CustomStyleViolation.DESCRIPTION_TOO_LONG,
            ),
        )
    }

    @Test
    fun `a blank sample is rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(sample = "  ")).contains(CustomStyleViolation.SAMPLE_BLANK),
        )
    }

    @Test
    fun `a sample at exactly one hundred eighty chars passes and one more fails`() {
        assertEquals(emptyList<CustomStyleViolation>(), validator.validate(validCustom().copy(sample = "s".repeat(180))))
        assertTrue(
            validator.validate(validCustom().copy(sample = "s".repeat(181))).contains(CustomStyleViolation.SAMPLE_TOO_LONG),
        )
    }

    @Test
    fun `blank instructions are rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(systemInstructions = "")).contains(
                CustomStyleViolation.INSTRUCTIONS_BLANK,
            ),
        )
    }

    @Test
    fun `instructions at exactly twenty-four hundred chars pass and one more fails`() {
        assertEquals(
            emptyList<CustomStyleViolation>(),
            validator.validate(validCustom().copy(systemInstructions = "i".repeat(2400))),
        )
        assertTrue(
            validator.validate(validCustom().copy(systemInstructions = "i".repeat(2401))).contains(
                CustomStyleViolation.INSTRUCTIONS_TOO_LONG,
            ),
        )
    }

    @Test
    fun `one few-shot is too few and six are too many`() {
        assertTrue(
            validator.validate(validCustom().copy(fewShots = listOf(shot()))).contains(
                CustomStyleViolation.TOO_FEW_FEWSHOTS,
            ),
        )
        assertTrue(
            validator.validate(
                validCustom().copy(fewShots = List(6) { index -> shot(raw = "raw $index", styled = "styled $index") }),
            ).contains(CustomStyleViolation.TOO_MANY_FEWSHOTS),
        )
    }

    @Test
    fun `two and five few-shots both pass`() {
        assertEquals(emptyList<CustomStyleViolation>(), validator.validate(validCustom()))
        assertEquals(
            emptyList<CustomStyleViolation>(),
            validator.validate(
                validCustom().copy(fewShots = List(5) { index -> shot(raw = "raw $index", styled = "styled $index") }),
            ),
        )
    }

    @Test
    fun `a blank few-shot side is rejected`() {
        assertTrue(
            validator.validate(validCustom().copy(fewShots = listOf(shot(raw = "  "), shot()))).contains(
                CustomStyleViolation.FEWSHOT_RAW_BLANK,
            ),
        )
        assertTrue(
            validator.validate(validCustom().copy(fewShots = listOf(shot(styled = ""), shot()))).contains(
                CustomStyleViolation.FEWSHOT_STYLED_BLANK,
            ),
        )
    }

    @Test
    fun `a fifteen-hundred-char few-shot side passes and one more fails`() {
        val ok = validCustom().copy(fewShots = listOf(shot(raw = "r".repeat(1500), styled = "s".repeat(1500)), shot()))
        assertEquals(emptyList<CustomStyleViolation>(), validator.validate(ok))
        val longRaw = validCustom().copy(fewShots = listOf(shot(raw = "r".repeat(1501)), shot()))
        assertTrue(validator.validate(longRaw).contains(CustomStyleViolation.FEWSHOT_RAW_TOO_LONG))
        val longStyled = validCustom().copy(fewShots = listOf(shot(styled = "s".repeat(1501)), shot()))
        assertTrue(validator.validate(longStyled).contains(CustomStyleViolation.FEWSHOT_STYLED_TOO_LONG))
    }

    @Test
    fun `every broken field is reported in one pass`() {
        val broken = validCustom().copy(
            id = "mine",
            kind = StyleKind.MECHANICAL,
            category = StyleCategory.SERIOUS,
            displayName = "",
            description = "",
            sample = "",
            systemInstructions = "",
            fewShots = listOf(shot(raw = "", styled = "")),
        )
        val violations = validator.validate(broken)
        assertTrue(
            violations.containsAll(
                listOf(
                    CustomStyleViolation.ID_PREFIX,
                    CustomStyleViolation.KIND_NOT_LLM,
                    CustomStyleViolation.CATEGORY_NOT_CUSTOM,
                    CustomStyleViolation.DISPLAY_NAME_BLANK,
                    CustomStyleViolation.DESCRIPTION_BLANK,
                    CustomStyleViolation.SAMPLE_BLANK,
                    CustomStyleViolation.INSTRUCTIONS_BLANK,
                    CustomStyleViolation.TOO_FEW_FEWSHOTS,
                    CustomStyleViolation.FEWSHOT_RAW_BLANK,
                    CustomStyleViolation.FEWSHOT_STYLED_BLANK,
                ),
            ),
            "missing violations: $violations",
        )
    }

    private fun shot(raw: String = "hear this", styled: String = "Styled: hear this."): FewShot =
        FewShot(raw = raw, styled = styled)

    private fun validCustom(): StyleDefinition =
        StyleDefinition(
            id = "custom-mine",
            displayName = "Mine",
            description = "How I write when nobody is watching.",
            sample = "Yeah, so we go tomorrow.",
            systemInstructions = "Restyle in my voice. NEVER change facts.",
            fewShots = listOf(shot(), shot(raw = "another try", styled = "Another try, styled.")),
            kind = StyleKind.LLM,
            category = StyleCategory.CUSTOM,
        )
}
