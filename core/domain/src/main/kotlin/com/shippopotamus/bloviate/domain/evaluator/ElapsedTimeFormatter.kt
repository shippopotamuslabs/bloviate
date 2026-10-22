package com.shippopotamus.bloviate.domain.evaluator

/** Pure `m:ss` formatting for the recording bar's elapsed-time readout. */
object ElapsedTimeFormatter {

    private const val MS_PER_SECOND: Long = 1_000L
    private const val SECONDS_PER_MINUTE: Long = 60L

    /** Formats [elapsedMs] as `m:ss`; negative input clamps to `0:00`. */
    fun format(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / MS_PER_SECOND).coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "%d:%02d".format(minutes, seconds)
    }
}
