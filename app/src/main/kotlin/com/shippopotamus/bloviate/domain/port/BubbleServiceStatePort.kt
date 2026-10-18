package com.shippopotamus.bloviate.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between the bubble overlay service (publisher) and the settings UI
 * (collector) for whether the service is currently running.
 */
interface BubbleServiceStatePort {

    val isServiceRunning: StateFlow<Boolean>

    fun publishServiceRunning(running: Boolean)
}
