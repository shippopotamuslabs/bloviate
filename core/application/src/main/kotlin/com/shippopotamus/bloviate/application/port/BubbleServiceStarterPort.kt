package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin

/**
 * The outcome of one bubble-service start request (BLO-189),
 * mirroring the [CustomStyleSyncOutcome] precedent: success and
 * refusal are both terminal outcomes, never a throw — a platform
 * refusal must never crash the caller, the bubble simply stays down
 * until the next request.
 */
sealed interface BubbleServiceStartOutcome {

    /** The service start was requested. */
    data object Started : BubbleServiceStartOutcome

    /** The start was refused — by the platform, or by the origin itself. */
    data object Refused : BubbleServiceStartOutcome
}

/**
 * The application-layer capability to start the bubble service
 * (BLO-189): the use case decides *whether* a start may happen, the
 * adapter owns *how* it is requested. Foreground-service mechanics
 * are the adapter's: which intent action a requestable origin maps
 * to, and how platform refusals surface.
 *
 * [BubbleServiceStartOrigin.BOOT] and
 * [BubbleServiceStartOrigin.USER] are requestable. [BubbleServiceStartOrigin.RESTART]
 * is a system-origin classification — the service restarting itself
 * after a kill — that can never be requested: adapters answer it
 * with [BubbleServiceStartOutcome.Refused] without touching the
 * platform. Platform refusals map to
 * [BubbleServiceStartOutcome.Refused], never an Android exception
 * across the port.
 */
interface BubbleServiceStarterPort {

    /** Requests the bubble service start for [origin]. */
    fun startBubble(origin: BubbleServiceStartOrigin): BubbleServiceStartOutcome
}
