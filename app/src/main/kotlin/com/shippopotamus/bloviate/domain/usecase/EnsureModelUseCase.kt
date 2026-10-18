package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.ModelStorePort
import javax.inject.Inject

/**
 * Ensures the transcription model is downloaded and verified (the status
 * screen's retry action). Safe to call when the model is already ready —
 * the store verifies and returns without downloading.
 */
class EnsureModelUseCase @Inject constructor(
    private val modelStorePort: ModelStorePort,
) {

    suspend operator fun invoke() {
        modelStorePort.ensureModel()
    }
}
