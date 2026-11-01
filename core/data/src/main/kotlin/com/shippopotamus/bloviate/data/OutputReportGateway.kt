package com.shippopotamus.bloviate.data

/**
 * The raw output-report callable surface (BLO-148): the narrow,
 * fake-testable seam under [FirebaseOutputReportClient], mirroring the
 * [CustomStyleSyncGateway] split. The call returns the callable's raw
 * `data` payload and throws [StyleFunctionException] carrying the
 * callable's error code on transport or server trouble. Shape checking
 * lives in the client, not here.
 */
interface OutputReportGateway {

    /**
     * Calls `submitOutputReport` with the report map (the exact contract
     * keys, provider/model present with null values when unknown);
     * returns `{reportId, duplicate}`.
     */
    suspend fun callSubmitOutputReport(report: Map<String, Any?>): Any?
}
