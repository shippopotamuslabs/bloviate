package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.application.port.BubbleAppearancePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's bubble appearance (opacity, size, shape, tint). */
class ObserveBubbleAppearanceUseCase @Inject constructor(
    private val bubbleAppearance: BubbleAppearancePort,
) {

    operator fun invoke(): StateFlow<BubbleAppearance> = bubbleAppearance.appearance
}
