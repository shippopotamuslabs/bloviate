package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.ParakeetModelStorePort
import javax.inject.Inject

/**
 * Ensures the Parakeet model set is downloaded and verified (the status
 * screen's Parakeet download action). Safe to call when the set is already
 * ready — the store verifies and returns without downloading.
 */
class EnsureParakeetModelUseCase @Inject constructor(
    private val parakeetModelStore: ParakeetModelStorePort,
) {

    suspend operator fun invoke() {
        parakeetModelStore.ensureModel()
    }
}
