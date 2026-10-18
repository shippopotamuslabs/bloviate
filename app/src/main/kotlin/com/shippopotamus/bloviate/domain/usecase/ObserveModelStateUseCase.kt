package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.domain.port.ModelStorePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the transcription model's lifecycle state (presence, download, failure). */
class ObserveModelStateUseCase @Inject constructor(
    private val modelStorePort: ModelStorePort,
) {

    operator fun invoke(): StateFlow<ModelState> = modelStorePort.state
}
