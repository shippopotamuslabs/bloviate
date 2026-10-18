package com.shippopotamus.bloviate.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the currently connected [FieldWatcherService] as a
 * [FocusedFieldOperator] so the bubble side can request insertion into the
 * focused field. The watcher registers itself in `onServiceConnected` and
 * unregisters in `onUnbind`/`onDestroy`; between connections (watcher
 * disabled, rebound by the system) the bridge is empty and insertion
 * reports the watcher unavailable instead of acting on a stale service.
 */
@Singleton
class FieldWatcherBridge @Inject constructor() {

    @Volatile
    private var operator: FocusedFieldOperator? = null

    fun register(operator: FocusedFieldOperator) {
        this.operator = operator
    }

    fun unregister(operator: FocusedFieldOperator) {
        if (this.operator === operator) {
            this.operator = null
        }
    }

    fun currentOperator(): FocusedFieldOperator? = operator
}
