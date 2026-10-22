package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.ModelState

/**
 * Whether the bubble shows its warming indication (ENC-126).
 *
 * Warming is exactly the engine-init window after process start: the
 * effective engine's model is present ([ModelState.Ready]) but the
 * initial warm-up has not completed, so a tap could not record yet.
 * Showing the mic in that window reads as broken — the bubble looks
 * tappable and isn't.
 *
 * Every other model state is NOT warming: while the model is absent,
 * downloading, or failed, the missing-model guidance belongs to the
 * Setup dashboard and the onboarding wizard, and the bubble keeps its
 * existing behavior. Once the initial warm-up has completed the engine
 * stays ready for the service's lifetime — a later memory-pressure
 * release re-warms lazily inside the next transcription, as before, and
 * never brings the warming state back.
 */
object EngineWarmingEvaluator {

    fun isWarming(modelState: ModelState, initialWarmUpCompleted: Boolean): Boolean =
        modelState is ModelState.Ready && !initialWarmUpCompleted
}
