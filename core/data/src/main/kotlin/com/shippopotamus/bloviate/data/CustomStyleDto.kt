package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.domain.model.FewShot
import com.shippopotamus.bloviate.domain.model.StyleCategory
import com.shippopotamus.bloviate.domain.model.StyleDefinition
import com.shippopotamus.bloviate.domain.model.StyleKind
import kotlinx.serialization.Serializable

/**
 * One persisted few-shot pair (BLO-27 S2).
 */
@Serializable
data class FewShotDto(
    val raw: String,
    val styled: String,
)

/**
 * The DataStore encoding of one [CustomStyleRecord] (BLO-27 S2, schema
 * v1).Enums travel as their names; [DataStoreCustomStyleStore] drops a
 * record whose kind/category name is unknown rather than guessing. The
 * DTO lives in `data/` so no persistence shape leaks into `domain/`
 * (see `contracts/DATA_LAYER_RULES.md` §3).
 */
@Serializable
data class CustomStyleDto(
    val id: String,
    val displayName: String,
    val description: String,
    val sample: String,
    val systemInstructions: String,
    val fewShots: List<FewShotDto>,
    val kind: String,
    val category: String,
    val revision: Long,
    val updatedAtEpochMillis: Long,
) {

    /** Decodes this DTO into a record, or null when it is malformed (unknown enum, blank id). */
    fun toRecord(): CustomStyleRecord? {
        if (id.isBlank()) return null
        val kind = runCatching { StyleKind.valueOf(kind) }.getOrNull() ?: return null
        val category = runCatching { StyleCategory.valueOf(category) }.getOrNull() ?: return null
        return CustomStyleRecord(
            definition = StyleDefinition(
                id = id,
                displayName = displayName,
                description = description,
                sample = sample,
                systemInstructions = systemInstructions,
                fewShots = fewShots.map { shot -> FewShot(raw = shot.raw, styled = shot.styled) },
                kind = kind,
                category = category,
            ),
            revision = revision,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    companion object {

        /** Encodes [record] for the cache. */
        fun from(record: CustomStyleRecord): CustomStyleDto =
            CustomStyleDto(
                id = record.definition.id,
                displayName = record.definition.displayName,
                description = record.definition.description,
                sample = record.definition.sample,
                systemInstructions = record.definition.systemInstructions,
                fewShots = record.definition.fewShots.map { shot ->
                    FewShotDto(raw = shot.raw, styled = shot.styled)
                },
                kind = record.definition.kind.name,
                category = record.definition.category.name,
                revision = record.revision,
                updatedAtEpochMillis = record.updatedAtEpochMillis,
            )
    }
}
