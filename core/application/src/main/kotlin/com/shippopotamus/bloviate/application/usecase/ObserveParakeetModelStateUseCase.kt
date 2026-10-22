package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.ModelState
import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the Parakeet model set's lifecycle state (presence, download, failure). */
class ObserveParakeetModelStateUseCase @Inject constructor(
    private val parakeetModelStore: ParakeetModelStorePort,
) {

    operator fun invoke(): StateFlow<ModelState> = parakeetModelStore.state
}
