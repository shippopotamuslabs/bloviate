package com.shippopotamus.bloviate.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the field watcher service is connected right now (ENC-129).
 * Enablement (a system-settings fact, read through
 * [WatcherAccessPort]) and connection (the service's own lifecycle)
 * are separate signals: Android can leave an enabled accessibility
 * service disconnected, and supervision exists to catch exactly that
 * gap. The watcher publishes from its connect/unbind callbacks;
 * Setup and the bubble service's notification consume the stream.
 */
interface WatcherConnectionPort {

    /** True while the watcher service is bound and connected. */
    val isConnected: StateFlow<Boolean>

    /** Publishes the watcher's connection state; called from its lifecycle only. */
    fun publishConnected(connected: Boolean)
}
