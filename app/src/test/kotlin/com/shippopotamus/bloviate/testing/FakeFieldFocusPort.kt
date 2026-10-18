package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hand-written fake of [FieldFocusPort] with the same deduped-emission semantics as the holder. */
class FakeFieldFocusPort : FieldFocusPort {

    private val mutableState = MutableStateFlow<FieldFocusState>(FieldFocusState.NoEditableFocus)

    override val fieldFocusState: StateFlow<FieldFocusState> = mutableState.asStateFlow()

    override fun publish(state: FieldFocusState) {
        mutableState.update { state }
    }
}
