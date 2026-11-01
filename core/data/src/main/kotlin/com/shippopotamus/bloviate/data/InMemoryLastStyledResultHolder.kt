package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LastStyledResultPort] as a plain in-memory [MutableStateFlow]
 * (BLO-148). Nothing is persisted — see the port's doc for the
 * deliberate session-only contract. Cleared by the sign-out and
 * account-deletion paths in the presentation/application layers.
 */
@Singleton
class InMemoryLastStyledResultHolder @Inject constructor() : LastStyledResultPort {

    private val mutableState = MutableStateFlow<LastStyledResult?>(null)

    override val lastResult: StateFlow<LastStyledResult?> = mutableState.asStateFlow()

    override fun record(result: LastStyledResult) {
        mutableState.value = result
    }

    override fun clear() {
        mutableState.value = null
    }
}
