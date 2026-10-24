package com.shippopotamus.bloviate.application.port

/**
 * Records one cloud-styling outcome for off-device diagnosis (BLO-167).
 * Client-side logging alone never leaves the device, so pre-call
 * failures such as no-auth are invisible without this seam. Reason
 * codes only — never transcript, styled text, or key material.
 */
interface StyleTelemetryPort {

    /**
     * Records the outcome of styling with [styleId]: [styled] is true
     * when the Function returned usable text, [reason] carries the
     * fallback code (null on success), and [cause] the failure that
     * produced it (null on success and on no-auth, which has none).
     * Must never throw: styling outcomes outrank telemetry.
     */
    fun recordStyleOutcome(styleId: String, styled: Boolean, reason: String?, cause: Throwable? = null)
}
