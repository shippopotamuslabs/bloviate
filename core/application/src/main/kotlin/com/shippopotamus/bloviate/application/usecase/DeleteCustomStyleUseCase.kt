package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import javax.inject.Inject

/** Deletes a custom style; unknown ids are a no-op. */
class DeleteCustomStyleUseCase @Inject constructor(
    private val customs: CustomStyleStorePort,
) {

    suspend operator fun invoke(styleId: String) {
        customs.delete(styleId)
    }
}
