package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind

/**
 * A custom style as plain draft values (BLO-176): the editor's fields
 * before any domain model exists. Few-shot pairs ride as raw/styled
 * string pairs — the draft-to-[StyleDefinition] construction lives
 * here in the application layer ([toDefinition]), so presentation
 * passes strings in and never builds domain models on the validate or
 * write path.
 */
data class CustomStyleDraft(
    val displayName: String,
    val description: String,
    val sample: String,
    val systemInstructions: String,
    val fewShotPairs: List<Pair<String, String>>,
)

/**
 * Builds the domain definition this draft describes: an LLM style in
 * [StyleCategory.CUSTOM] whose id is [styleId] — blank for a create,
 * where the server allocates the id on save.
 */
fun CustomStyleDraft.toDefinition(styleId: String?): StyleDefinition = StyleDefinition(
    id = styleId.orEmpty(),
    displayName = displayName,
    description = description,
    sample = sample,
    systemInstructions = systemInstructions,
    fewShots = fewShotPairs.map { pair -> FewShot(raw = pair.first, styled = pair.second) },
    kind = StyleKind.LLM,
    category = StyleCategory.CUSTOM,
)

/**
 * The definition validation actually runs against: a create carries no
 * id yet (the server allocates one), but the validator requires the
 * `custom-` prefix — so a blank id validates against a placeholder and
 * every other field validates as given. The placeholder is never sent
 * or stored; the write path always carries the blank id.
 */
internal fun StyleDefinition.forCustomStyleValidation(): StyleDefinition =
    if (id.isBlank()) copy(id = CREATE_ID_PLACEHOLDER) else this

/** Temporary validation-only id for creates; never sent to the server (see [forCustomStyleValidation]). */
private const val CREATE_ID_PLACEHOLDER: String = "custom-pending"
