package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.CustomStyleGenerationOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleGenerationPort
import com.shippopotamus.bloviate.application.port.GeneratedStyleDraft
import com.shippopotamus.bloviate.domain.model.FewShot

/**
 * Hand-written fake of [CustomStyleGenerationPort]. [result] scripts the
 * next call (a handler answers every call when set); every description is
 * recorded so tests assert on state, not on the process.
 */
class FakeCustomStyleGenerationPort(
    var result: CustomStyleGenerationOutcome = CustomStyleGenerationOutcome.Ok(
        GeneratedStyleDraft(
            displayName = "Noir Detective",
            description = "Rain, neon, and regret.",
            sample = "The rain kept falling, and so did I.",
            systemInstructions = "Short sentences. Rain everywhere.",
            fewShots = listOf(
                FewShot(raw = "i walked home late", styled = "I walked home late, the rain keeping time."),
                FewShot(raw = "the meeting is at noon", styled = "The meeting is at noon, high noon."),
            ),
        ),
    ),
    var handler: ((String) -> CustomStyleGenerationOutcome)? = null,
) : CustomStyleGenerationPort {

    private val mutableDescriptions = mutableListOf<String>()

    /** Descriptions passed to [generate] so far, in order. */
    val descriptions: List<String> get() = mutableDescriptions.toList()

    override suspend fun generate(description: String): CustomStyleGenerationOutcome {
        mutableDescriptions += description
        return handler?.invoke(description) ?: result
    }
}
