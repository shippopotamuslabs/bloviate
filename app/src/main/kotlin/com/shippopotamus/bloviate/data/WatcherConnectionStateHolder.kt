package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.port.WatcherConnectionPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder behind [WatcherConnectionPort], written by the
 * field watcher service in its connect/unbind callbacks. Same
 * conflation semantics as [FieldFocusStateHolder]: equal values are
 * never re-emitted. Starts disconnected — in a fresh process the
 * watcher has not connected yet, and claiming otherwise would hide
 * exactly the state supervision exists to surface.
 */
@Singleton
class WatcherConnectionStateHolder @Inject constructor() : WatcherConnectionPort {

    private val mutableState = MutableStateFlow(false)

    override val isConnected: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publishConnected(connected: Boolean) {
        mutableState.update { connected }
    }
}
