package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import com.shippopotamus.bloviate.application.port.OutputReportOutcome
import com.shippopotamus.bloviate.application.port.OutputReportPort
import com.shippopotamus.bloviate.application.port.OutputReportReason
import com.shippopotamus.bloviate.application.port.OutputReportRequest
import javax.inject.Inject

/** The outcome of one report attempt through [SubmitOutputReportUseCase] (BLO-148). */
sealed interface SubmitOutputReportOutcome {

    /** The server stored the report; [duplicate] marks an idempotent replay of the same id. */
    data class Submitted(val duplicate: Boolean) : SubmitOutputReportOutcome

    /** This session holds no styled result to report; nothing was sent. */
    data object NoResult : SubmitOutputReportOutcome

    /** Signed out; nothing was sent. */
    data object SignedOut : SubmitOutputReportOutcome

    /** The submission failed; retryable with the same client report id. */
    data object Unavailable : SubmitOutputReportOutcome
}

/**
 * Submits the user-initiated report of the session's last styled
 * dictation (BLO-148). The request is built from the CURRENT
 * [LastStyledResultPort] record at submit time — the content leaves
 * the device only when the user picks a reason and this runs. With no
 * record, the outcome is [SubmitOutputReportOutcome.NoResult] and the
 * port is never called. [clientReportId] is supplied by the caller so
 * retries of one picker session reuse it and cannot duplicate the
 * report server-side.
 */
class SubmitOutputReportUseCase @Inject constructor(
    private val reportPort: OutputReportPort,
    private val lastResultPort: LastStyledResultPort,
) {

    suspend operator fun invoke(
        reason: OutputReportReason,
        appVersion: String,
        clientReportId: String,
    ): SubmitOutputReportOutcome {
        val last = lastResultPort.lastResult.value
            ?: return SubmitOutputReportOutcome.NoResult
        val request = OutputReportRequest(
            clientReportId = clientReportId,
            styledOutput = last.styledOutput,
            sourceTranscript = last.sourceTranscript,
            reason = reason,
            styleId = last.styleId,
            provider = last.provider,
            model = last.model,
            appVersion = appVersion,
        )
        return when (val outcome = reportPort.submit(request)) {
            is OutputReportOutcome.Submitted -> SubmitOutputReportOutcome.Submitted(outcome.duplicate)
            OutputReportOutcome.SignedOut -> SubmitOutputReportOutcome.SignedOut
            OutputReportOutcome.Unavailable -> SubmitOutputReportOutcome.Unavailable
        }
    }
}
