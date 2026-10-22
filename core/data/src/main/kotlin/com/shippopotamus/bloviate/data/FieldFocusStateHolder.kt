package com.shippopotamus.bloviate.data

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.application.port.FieldFocusPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder behind [FieldFocusPort]. `MutableStateFlow` conflates equal
 * values, so duplicate states are never re-emitted to collectors.
 */
@Singleton
class FieldFocusStateHolder @Inject constructor() : FieldFocusPort {

    private val mutableState = MutableStateFlow<FieldFocusState>(FieldFocusState.NoEditableFocus)

    override val fieldFocusState: StateFlow<FieldFocusState> = mutableState.asStateFlow()

    override fun publish(state: FieldFocusState) {
        mutableState.update { state }
    }
}
