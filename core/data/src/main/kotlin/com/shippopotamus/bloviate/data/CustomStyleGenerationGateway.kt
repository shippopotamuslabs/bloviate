package com.shippopotamus.bloviate.data

/**
 * The raw style-draft generation callable surface (BLO-160 S7): the narrow,
 * fake-testable seam under [FirebaseCustomStyleGenerationClient],
 * mirroring the [CustomStyleSyncGateway] split. Returns the callable's raw
 * `data` payload (or null when it carried none) and throws
 * [StyleFunctionException] carrying the callable's error code on transport
 * or server trouble. Shape checking lives in the client, not here — a fake
 * implements this interface with canned maps.
 */
interface CustomStyleGenerationGateway {

    /**
     * Calls `generateCustomStyle` with exactly `{description}`; returns
     * `{draft}`.
     */
    suspend fun callGenerateCustomStyle(description: String): Any?
}
