package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.WatcherConnectionPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [WatcherConnectionPort]; publish connection states directly. */
class FakeWatcherConnectionPort : WatcherConnectionPort {

    private val mutableState = MutableStateFlow(false)

    override val isConnected: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publishConnected(connected: Boolean) {
        mutableState.value = connected
    }
}
