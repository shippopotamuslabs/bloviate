package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between the accessibility watcher (publisher) and the bubble
 * overlay (collector) for notification-shade presence (ENC-164).
 *
 * The shade is a deterministic no-op for the bubble: while it is
 * present the overlay swallows every visibility command at the point
 * of effect, so the resident window stays exactly as commanded and a
 * live recording keeps recording invisibly underneath. When the shade
 * lifts, the overlay resyncs the visibility machine's belief against
 * the window's actual visibility. The signal is a bare boolean: no
 * window content, package, or field text is ever read for it.
 * Emissions are deduped: collectors only see actual transitions.
 */
interface ShadePresencePort {

    val shadePresent: StateFlow<Boolean>

    fun publish(present: Boolean)
}
