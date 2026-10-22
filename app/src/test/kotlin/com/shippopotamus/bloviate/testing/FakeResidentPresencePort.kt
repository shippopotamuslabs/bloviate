package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [ResidentPresencePort]: an in-memory StateFlow plus a save record. */
class FakeResidentPresencePort : ResidentPresencePort {

    private val mutableState = MutableStateFlow(ResidentPresenceState.DEFAULT)

    override val state: StateFlow<ResidentPresenceState> = mutableState.asStateFlow()

    val savedStates = mutableListOf<ResidentPresenceState>()

    var saveCallCount: Int = 0
        private set

    override suspend fun save(state: ResidentPresenceState) {
        saveCallCount++
        savedStates += state
        mutableState.value = state
    }

    /** Publishes a record as if another writer (the store, the service) had saved it. */
    fun publish(state: ResidentPresenceState) {
        mutableState.value = state
    }
}
