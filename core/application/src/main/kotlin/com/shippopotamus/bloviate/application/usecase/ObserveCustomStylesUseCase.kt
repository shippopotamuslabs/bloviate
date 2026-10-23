package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.CustomStylesState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the custom-styles cache snapshot (records plus the loaded flag). */
class ObserveCustomStylesUseCase @Inject constructor(
    private val customs: CustomStyleStorePort,
) {

    operator fun invoke(): StateFlow<CustomStylesState> = customs.state
}
