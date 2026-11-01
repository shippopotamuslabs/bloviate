package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportPort
import com.shippopotamus.bloviate.application.port.OutputReportRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [OutputReportPort] via the `submitOutputReport` callable (BLO-148),
 * mirroring [FirebaseCustomStyleSyncClient]: a signed-out user gets
 * [OutputReportOutcome.SignedOut] without a network call, and every
 * failure becomes a typed outcome, never a throw (except coroutine
 * cancellation). Logs carry the reason and outcome codes ONLY — never
 * the styled output, the transcript, or any other report content.
 */
class FirebaseOutputReportClient @Inject constructor(
    private val gateway: OutputReportGateway,
    private val auth: AuthSessionPort,
) : OutputReportPort {

    override suspend fun submit(report: OutputReportRequest): OutputReportOutcome {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "output-report outcome=signed-out reason=${report.reason.wireValue}")
            return OutputReportOutcome.SignedOut
        }
        return try {
            val payload = gateway.callSubmitOutputReport(report.toRequestMap()) as? Map<*, *>
            val duplicate = payload?.get("duplicate") as? Boolean
            val reportId = payload?.get("reportId") as? String
            if (duplicate == null && reportId == null) {
                Log.d(TAG, "output-report outcome=unavailable reason=${report.reason.wireValue}")
                return OutputReportOutcome.Unavailable
            }
            Log.d(TAG, "output-report outcome=submitted reason=${report.reason.wireValue}")
            OutputReportOutcome.Submitted(duplicate = duplicate ?: false)
        } catch (exception: StyleFunctionException) {
            val outcome = if (exception.code == CODE_UNAUTHENTICATED) {
                OutputReportOutcome.SignedOut
            } else {
                OutputReportOutcome.Unavailable
            }
            Log.d(TAG, "output-report outcome=$outcome reason=${report.reason.wireValue} code=${exception.code}")
            outcome
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "output-report outcome=unavailable reason=${report.reason.wireValue}", exception)
            OutputReportOutcome.Unavailable
        }
    }

    /**
     * The exact callable contract keys (BLO-148). Provider and model
     * keys are always present — with null values when the styling call
     * reported no provenance — matching the sync gateway's null-carrying
     * maps.
     */
    private fun OutputReportRequest.toRequestMap(): Map<String, Any?> =
        mapOf(
            "clientReportId" to clientReportId,
            "styledOutput" to styledOutput,
            "sourceTranscript" to sourceTranscript,
            "reason" to reason.wireValue,
            "styleId" to styleId,
            "provider" to provider,
            "model" to model,
            "appVersion" to appVersion,
        )

    private companion object {
        const val TAG: String = "OutputReport"
        const val CODE_UNAUTHENTICATED: String = "unauthenticated"
    }
}
