package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.port.BubbleAppearancePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's bubble appearance (opacity, size, shape, tint). */
class ObserveBubbleAppearanceUseCase @Inject constructor(
    private val bubbleAppearance: BubbleAppearancePort,
) {

    operator fun invoke(): StateFlow<BubbleAppearance> = bubbleAppearance.appearance
}
