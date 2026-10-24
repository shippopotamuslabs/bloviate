package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * Hand-written fake of [CustomStyleSyncPort]. [listResult] scripts the
 * list call; [saveHandler] answers saves (default: echoes the definition
 * back with the next revision); [deleteResult] scripts deletes. Every
 * call is recorded so tests assert on state, not on the process.
 */
class FakeCustomStyleSyncPort(
    var listResult: CustomStyleSyncOutcome<List<CustomStyleRecord>> =
        CustomStyleSyncOutcome.Ok(emptyList()),
    var saveHandler: (StyleDefinition, Long?) -> CustomStyleSyncOutcome<CustomStyleRecord> =
        { definition, expectedRevision ->
            CustomStyleSyncOutcome.Ok(
                CustomStyleRecord(
                    definition = definition,
                    revision = (expectedRevision ?: 0L) + 1L,
                    updatedAtEpochMillis = 1L,
                ),
            )
        },
    var deleteResult: CustomStyleSyncOutcome<Boolean> = CustomStyleSyncOutcome.Ok(true),
) : CustomStyleSyncPort {

    var listCallCount: Int = 0
        private set

    private val mutableSavedInputs = mutableListOf<Pair<StyleDefinition, Long?>>()

    /** (definition, expectedRevision) pairs passed to [save] so far, in order. */
    val savedInputs: List<Pair<StyleDefinition, Long?>> get() = mutableSavedInputs.toList()

    private val mutableDeletedIds = mutableListOf<String>()

    /** Style ids passed to [delete] so far, in order. */
    val deletedIds: List<String> get() = mutableDeletedIds.toList()

    override suspend fun list(): CustomStyleSyncOutcome<List<CustomStyleRecord>> {
        listCallCount++
        return listResult
    }

    override suspend fun save(
        definition: StyleDefinition,
        expectedRevision: Long?,
    ): CustomStyleSyncOutcome<CustomStyleRecord> {
        mutableSavedInputs += definition to expectedRevision
        return saveHandler(definition, expectedRevision)
    }

    override suspend fun delete(styleId: String): CustomStyleSyncOutcome<Boolean> {
        mutableDeletedIds += styleId
        return deleteResult
    }
}
