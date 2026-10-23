package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.KeyboardVisibilityPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder behind [KeyboardVisibilityPort]. `MutableStateFlow`
 * conflates equal values, so duplicate visibility reports are never
 * re-emitted to collectors.
 */
@Singleton
class KeyboardVisibilityStateHolder @Inject constructor() : KeyboardVisibilityPort {

    private val mutableState = MutableStateFlow(false)

    override val keyboardVisible: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publish(visible: Boolean) {
        mutableState.update { visible }
    }
}
