package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.ModelState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineWarmingEvaluatorTest {

    @Test
    fun `model ready with the initial warm-up outstanding is warming`() {
        assertTrue(EngineWarmingEvaluator.isWarming(ModelState.Ready, initialWarmUpCompleted = false))
    }

    @Test
    fun `model ready with the initial warm-up completed is not warming`() {
        assertFalse(EngineWarmingEvaluator.isWarming(ModelState.Ready, initialWarmUpCompleted = true))
    }

    @Test
    fun `model absent is not warming - the missing-model case belongs to Setup and the wizard`() {
        assertFalse(EngineWarmingEvaluator.isWarming(ModelState.Absent, initialWarmUpCompleted = false))
    }

    @Test
    fun `model downloading is not warming`() {
        assertFalse(
            EngineWarmingEvaluator.isWarming(
                ModelState.Downloading(progressFraction = 0.5f),
                initialWarmUpCompleted = false,
            ),
        )
    }

    @Test
    fun `model failed is not warming`() {
        assertFalse(
            EngineWarmingEvaluator.isWarming(
                ModelState.Failed(reason = "integrity check failed"),
                initialWarmUpCompleted = false,
            ),
        )
    }
}
