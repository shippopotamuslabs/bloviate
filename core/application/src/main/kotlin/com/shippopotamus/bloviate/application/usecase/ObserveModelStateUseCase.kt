package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.application.port.ModelStorePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the transcription model's lifecycle state (presence, download, failure). */
class ObserveModelStateUseCase @Inject constructor(
    private val modelStorePort: ModelStorePort,
) {

    operator fun invoke(): StateFlow<ModelState> = modelStorePort.state
}
