package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.application.port.ShadePresencePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder behind [ShadePresencePort]. `MutableStateFlow`
 * conflates equal values, so duplicate shade reports are never
 * re-emitted to collectors.
 */
@Singleton
class ShadePresenceStateHolder @Inject constructor() : ShadePresencePort {

    private val mutableState = MutableStateFlow(false)

    override val shadePresent: StateFlow<Boolean> = mutableState.asStateFlow()

    override fun publish(present: Boolean) {
        mutableState.update { present }
    }
}
