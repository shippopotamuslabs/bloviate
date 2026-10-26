package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin

/**
 * What a bubble-service start request is (BLO-189): the
 * application-owned start vocabulary. Only these two starts can ever
 * be requested — through this port — so only these two exist here.
 * [BubbleServiceStartOrigin.RESTART] stays a domain-side
 * classification (the service restarting itself after a kill) and is
 * unrepresentable as a request.
 */
enum class BubbleStartRequest {

    /** A system-triggered re-arm: boot, or the app updated under a running bubble. */
    BOOT,

    /** An explicit in-app start, always mic-eligible. */
    USER,
}

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

    /** The start was refused by the platform. */
    data object Refused : BubbleServiceStartOutcome
}

/**
 * The application-layer capability to start the bubble service
 * (BLO-189): the use case decides *whether* a start may happen, the
 * adapter owns *how* it is requested. Foreground-service mechanics
 * are the adapter's: which intent action a [BubbleStartRequest] maps
 * to, and how platform refusals surface. Platform refusals map to
 * [BubbleServiceStartOutcome.Refused], never an Android exception
 * across the port.
 */
interface BubbleServiceStarterPort {

    /** Requests the bubble service start for [request]. */
    fun startBubble(request: BubbleStartRequest): BubbleServiceStartOutcome
}
