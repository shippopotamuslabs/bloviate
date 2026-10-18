package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.FieldFocusState
import com.shippopotamus.bloviate.domain.port.FieldFocusPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the current field-focus classification for presentation and overlay consumers. */
class ObserveFieldFocusUseCase @Inject constructor(
    private val fieldFocusPort: FieldFocusPort,
) {

    operator fun invoke(): StateFlow<FieldFocusState> = fieldFocusPort.fieldFocusState
}
