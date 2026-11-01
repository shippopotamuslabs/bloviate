package com.shippopotamus.bloviate.application.port

/** Why the user is reporting a styled output (BLO-148); [wireValue] is the callable's reason string. */
enum class OutputReportReason(val wireValue: String) {
    OFFENSIVE("offensive"),
    SEXUAL("sexual"),
    HATEFUL("hateful"),
    WRONG("wrong"),
    OTHER("other"),
}

/**
 * One user-initiated output report (BLO-148), built by
 * `SubmitOutputReportUseCase` from the session's [LastStyledResult]
 * plus the caller's reason, app version, and idempotency id. The
 * content fields leave the device ONLY inside this explicit submission
 * — opening the reason picker never builds one.
 */
data class OutputReportRequest(
    val clientReportId: String,
    val styledOutput: String,
    val sourceTranscript: String,
    val reason: OutputReportReason,
    val styleId: String,
    val provider: String?,
    val model: String?,
    val appVersion: String,
)

/** The outcome of one report submission (BLO-148): terminal, never a throw. */
sealed interface OutputReportOutcome {

    /** The server stored the report; [duplicate] marks an idempotent replay of the same id. */
    data class Submitted(val duplicate: Boolean) : OutputReportOutcome

    /** No signed-in user; the caller never reached the network. */
    data object SignedOut : OutputReportOutcome

    /** Transport trouble or a rejected payload; retryable with the same client report id. */
    data object Unavailable : OutputReportOutcome
}

/**
 * The server side of output reporting (BLO-148): the
 * `submitOutputReport` callable, which binds the report to the
 * caller's verified uid server-side. Implementations map every failure
 * onto [OutputReportOutcome], never a throw (except coroutine
 * cancellation, which is rethrown). Logs carry reason/outcome codes
 * only — never the reported content.
 */
interface OutputReportPort {

    /** Submits [report]; idempotent on (uid, clientReportId) server-side. */
    suspend fun submit(report: OutputReportRequest): OutputReportOutcome
}
