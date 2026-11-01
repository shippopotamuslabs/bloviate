package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [LastStyledResultPort]: a plain in-memory record with a clear counter. */
class FakeLastStyledResultPort(
    initial: LastStyledResult? = null,
) : LastStyledResultPort {

    private val mutableState = MutableStateFlow(initial)

    override val lastResult: StateFlow<LastStyledResult?> = mutableState.asStateFlow()

    var clearCallCount: Int = 0
        private set

    override fun record(result: LastStyledResult) {
        mutableState.value = result
    }

    override fun clear() {
        clearCallCount++
        mutableState.value = null
    }
}
