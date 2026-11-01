package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The most recent styled dictation of this process session (BLO-148):
 * the provenance a user-initiated output report submits. [sourceTranscript]
 * is the local cleaned text that was sent for styling; [styledOutput]
 * is the text the model returned; [model]/[provider] are the server's
 * own report of what answered, or null when it did not say.
 */
data class LastStyledResult(
    val styleId: String,
    val sourceTranscript: String,
    val styledOutput: String,
    val model: String?,
    val provider: String?,
    val recordedAtEpochMillis: Long,
)

/**
 * Session-scoped holder for the [LastStyledResult] (BLO-148).
 *
 * IN-MEMORY ONLY, deliberately: the record is never persisted, so a
 * report is possible for the most recent styled dictation of this
 * process session only. Once a newer styled dictation replaces it, the
 * process dies, or [clear] runs (sign-out, account deletion), it is
 * gone and nothing is stored anywhere. Older dictations carry no
 * reportable record — their provenance was never fabricated or saved.
 */
interface LastStyledResultPort {

    /** The current record, or null when this session has none. */
    val lastResult: StateFlow<LastStyledResult?>

    /** Replaces the current record with [result]. */
    fun record(result: LastStyledResult)

    /** Drops the current record. */
    fun clear()
}
