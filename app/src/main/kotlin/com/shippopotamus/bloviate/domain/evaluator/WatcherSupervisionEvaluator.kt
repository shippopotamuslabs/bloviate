package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.WatcherSupervisionState

/**
 * Derives the watcher's supervised health (ENC-129) from its two
 * signals. Enablement dominates: a watcher the user never turned on
 * is [WatcherSupervisionState.DISABLED] regardless of connection
 * bookkeeping, because there is nothing to reconnect. Only an enabled
 * watcher can be [WatcherSupervisionState.DISCONNECTED] — the silent
 * failure this supervision exists to surface.
 */
object WatcherSupervisionEvaluator {

    fun resolve(
        watcherEnabled: Boolean,
        watcherConnected: Boolean,
    ): WatcherSupervisionState =
        when {
            !watcherEnabled -> WatcherSupervisionState.DISABLED
            watcherConnected -> WatcherSupervisionState.CONNECTED
            else -> WatcherSupervisionState.DISCONNECTED
        }
}
