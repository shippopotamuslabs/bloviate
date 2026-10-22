package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.BubbleServiceStatePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder behind [BubbleServiceStatePort], written by the bubble
 * service in its create/destroy. Same conflation semantics as
 * [FieldFocusStateHolder]: equal values are never re-emitted.
 */
@Singleton
class BubbleServiceStateHolder @Inject constructor() : BubbleServiceStatePort {

    private val mutableState = MutableStateFlow(false)

    override val isServiceRunning: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publishServiceRunning(running: Boolean) {
        mutableState.update { running }
    }
}
