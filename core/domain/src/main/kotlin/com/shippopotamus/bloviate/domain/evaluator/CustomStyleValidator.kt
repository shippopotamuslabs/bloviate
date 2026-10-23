package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.CUSTOM_STYLE_ID_PREFIX
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import javax.inject.Inject

/**
 * One rule a [StyleDefinition] can break as a user-created custom style.
 * Every entry maps to exactly one documented limit, so a UI can explain
 * each rejection without parsing messages.
 */
enum class CustomStyleViolation {
    ID_PREFIX,
    ID_SUFFIX_BLANK,
    KIND_NOT_LLM,
    CATEGORY_NOT_CUSTOM,
    DISPLAY_NAME_BLANK,
    DISPLAY_NAME_TOO_LONG,
    DESCRIPTION_BLANK,
    DESCRIPTION_TOO_LONG,
    SAMPLE_BLANK,
    SAMPLE_TOO_LONG,
    INSTRUCTIONS_BLANK,
    INSTRUCTIONS_TOO_LONG,
    TOO_FEW_FEWSHOTS,
    TOO_MANY_FEWSHOTS,
    FEWSHOT_RAW_BLANK,
    FEWSHOT_RAW_TOO_LONG,
    FEWSHOT_STYLED_BLANK,
    FEWSHOT_STYLED_TOO_LONG,
}

/**
 * Validates a [StyleDefinition] as a user-created custom style (ENC-199).
 * Customs are LLM styles in [StyleCategory.CUSTOM] whose ids start with
 * [CUSTOM_STYLE_ID_PREFIX]; every text field must be non-blank within its
 * length cap, with 2–5 non-blank few-shot pairs. Pure and framework-free;
 * the write path ([SaveCustomStyleUseCase]) saves only when [validate]
 * returns empty.
 */
class CustomStyleValidator @Inject constructor() {

    /** Every violated rule, in field order; empty means [definition] is a valid custom style. */
    fun validate(definition: StyleDefinition): List<CustomStyleViolation> =
        buildList {
            addAll(validateIdentity(definition = definition))
            addAll(validateDisplayFields(definition = definition))
            addAll(validateVoice(definition = definition))
        }

    private fun validateIdentity(definition: StyleDefinition): List<CustomStyleViolation> =
        buildList {
            if (!definition.id.startsWith(CUSTOM_STYLE_ID_PREFIX)) {
                add(CustomStyleViolation.ID_PREFIX)
            } else if (definition.id.removePrefix(CUSTOM_STYLE_ID_PREFIX).isBlank()) {
                add(CustomStyleViolation.ID_SUFFIX_BLANK)
            }
            if (definition.kind != StyleKind.LLM) {
                add(CustomStyleViolation.KIND_NOT_LLM)
            }
            if (definition.category != StyleCategory.CUSTOM) {
                add(CustomStyleViolation.CATEGORY_NOT_CUSTOM)
            }
        }

    private fun validateDisplayFields(definition: StyleDefinition): List<CustomStyleViolation> =
        buildList {
            val displayName = definition.displayName.trim()
            if (displayName.isEmpty()) {
                add(CustomStyleViolation.DISPLAY_NAME_BLANK)
            } else if (displayName.length > MAX_DISPLAY_NAME_CHARS) {
                add(CustomStyleViolation.DISPLAY_NAME_TOO_LONG)
            }
            if (definition.description.isBlank()) {
                add(CustomStyleViolation.DESCRIPTION_BLANK)
            } else if (definition.description.length > MAX_DESCRIPTION_CHARS) {
                add(CustomStyleViolation.DESCRIPTION_TOO_LONG)
            }
            if (definition.sample.isBlank()) {
                add(CustomStyleViolation.SAMPLE_BLANK)
            } else if (definition.sample.length > MAX_SAMPLE_CHARS) {
                add(CustomStyleViolation.SAMPLE_TOO_LONG)
            }
        }

    private fun validateVoice(definition: StyleDefinition): List<CustomStyleViolation> =
        buildList {
            if (definition.systemInstructions.isBlank()) {
                add(CustomStyleViolation.INSTRUCTIONS_BLANK)
            } else if (definition.systemInstructions.length > MAX_INSTRUCTIONS_CHARS) {
                add(CustomStyleViolation.INSTRUCTIONS_TOO_LONG)
            }
            if (definition.fewShots.size < MIN_FEWSHOTS) {
                add(CustomStyleViolation.TOO_FEW_FEWSHOTS)
            } else if (definition.fewShots.size > MAX_FEWSHOTS) {
                add(CustomStyleViolation.TOO_MANY_FEWSHOTS)
            }
            definition.fewShots.forEach { shot ->
                if (shot.raw.isBlank()) {
                    add(CustomStyleViolation.FEWSHOT_RAW_BLANK)
                } else if (shot.raw.length > MAX_FEWSHOT_SIDE_CHARS) {
                    add(CustomStyleViolation.FEWSHOT_RAW_TOO_LONG)
                }
                if (shot.styled.isBlank()) {
                    add(CustomStyleViolation.FEWSHOT_STYLED_BLANK)
                } else if (shot.styled.length > MAX_FEWSHOT_SIDE_CHARS) {
                    add(CustomStyleViolation.FEWSHOT_STYLED_TOO_LONG)
                }
            }
        }

    private companion object {
        const val MAX_DISPLAY_NAME_CHARS: Int = 40
        const val MAX_DESCRIPTION_CHARS: Int = 240
        const val MAX_SAMPLE_CHARS: Int = 512
        const val MAX_INSTRUCTIONS_CHARS: Int = 6000
        const val MIN_FEWSHOTS: Int = 2
        const val MAX_FEWSHOTS: Int = 5
        const val MAX_FEWSHOT_SIDE_CHARS: Int = 1500
    }
}
