package com.shippopotamus.bloviate.data

/**
 * The raw custom-styles callable surface (BLO-25 S4): the narrow,
 * fake-testable seam under [FirebaseCustomStyleSyncClient], mirroring
 * the [StyleFunctionGateway] split. Each call returns the callable's raw
 * `data` payload (or null when it carried none) and throws
 * [StyleFunctionException] carrying the callable's error code on
 * transport or server trouble. Shape checking lives in the client, not
 * here — a fake implements this interface with canned maps.
 */
interface CustomStyleSyncGateway {

    /** Calls `listCustomStyles` with `{}`; returns `{styles, syncedAtEpochMillis}`. */
    suspend fun callListCustomStyles(): Any?

    /**
     * Calls `saveCustomStyle` with `{definition, expectedRevision?}`;
     * [definition] carries `displayName`, `description`, `sample`,
     * `systemInstructions`, `fewShots` and, on update only, `id`.
     * Returns `{style}`.
     */
    suspend fun callSaveCustomStyle(
        definition: Map<String, Any?>,
        expectedRevision: Long?,
    ): Any?

    /** Calls `deleteCustomStyle` with `{id}`; returns `{deleted}`. */
    suspend fun callDeleteCustomStyle(id: String): Any?
}
