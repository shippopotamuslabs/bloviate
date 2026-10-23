package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.CustomStyleRecord
import com.shippopotamus.bloviate.application.port.CustomStyleSyncOutcome
import com.shippopotamus.bloviate.application.port.CustomStyleSyncPort
import com.shippopotamus.bloviate.domain.model.StyleDefinition

/**
 * Hand-written fake of [CustomStyleSyncPort] for ViewModel tests.
 * Scripts list/save/delete outcomes and records every call; mirrors the
 * core-application fake of the same name (each test source set owns its
 * fakes — Bloviate has no shared `:core:testing` module yet).
 */
class FakeCustomStyleSyncPort(
    var listResult: CustomStyleSyncOutcome<List<CustomStyleRecord>> =
        CustomStyleSyncOutcome.Ok(emptyList()),
    var deleteResult: CustomStyleSyncOutcome<Boolean> = CustomStyleSyncOutcome.Ok(true),
) : CustomStyleSyncPort {

    var listCallCount: Int = 0
        private set

    override suspend fun list(): CustomStyleSyncOutcome<List<CustomStyleRecord>> {
        listCallCount++
        return listResult
    }

    override suspend fun save(
        definition: StyleDefinition,
        expectedRevision: Long?,
    ): CustomStyleSyncOutcome<CustomStyleRecord> =
        throw UnsupportedOperationException("FakeCustomStyleSyncPort never saves in ViewModel tests")

    override suspend fun delete(styleId: String): CustomStyleSyncOutcome<Boolean> = deleteResult
}
