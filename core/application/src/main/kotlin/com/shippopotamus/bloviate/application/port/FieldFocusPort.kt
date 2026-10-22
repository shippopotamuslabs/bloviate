package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between the accessibility watcher (publisher) and the bubble overlay
 * plus settings UI (collectors). Emissions are deduped: collectors only see
 * actual state transitions.
 */
interface FieldFocusPort {

    val fieldFocusState: StateFlow<FieldFocusState>

    fun publish(state: FieldFocusState)
}
