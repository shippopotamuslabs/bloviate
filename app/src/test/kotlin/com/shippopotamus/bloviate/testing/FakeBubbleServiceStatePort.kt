package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.BubbleServiceStatePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hand-written fake of [BubbleServiceStatePort] with the holder's conflation semantics. */
class FakeBubbleServiceStatePort : BubbleServiceStatePort {

    private val mutableState = MutableStateFlow(false)

    override val isServiceRunning: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publishServiceRunning(running: Boolean) {
        mutableState.update { running }
    }
}
