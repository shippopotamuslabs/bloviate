package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.KeyboardVisibilityPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hand-written fake of [KeyboardVisibilityPort] with the same deduped-emission semantics as the holder. */
class FakeKeyboardVisibilityPort : KeyboardVisibilityPort {

    private val mutableState = MutableStateFlow(false)

    override val keyboardVisible: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publish(visible: Boolean) {
        mutableState.update { visible }
    }
}
