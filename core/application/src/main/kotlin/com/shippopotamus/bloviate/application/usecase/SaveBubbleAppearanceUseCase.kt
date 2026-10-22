package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.application.port.BubbleAppearancePort
import javax.inject.Inject

/** Persists the user's bubble appearance; the showing bubble restyles on the next emission. */
class SaveBubbleAppearanceUseCase @Inject constructor(
    private val bubbleAppearance: BubbleAppearancePort,
) {

    suspend operator fun invoke(appearance: BubbleAppearance) {
        bubbleAppearance.save(appearance)
    }
}
