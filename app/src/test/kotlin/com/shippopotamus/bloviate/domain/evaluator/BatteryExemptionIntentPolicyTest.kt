package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The battery-exemption attempt plan (ENC-139): the one-tap system
 * dialog first, the settings list as the fallback that can never
 * dead-end, and nothing after the list.
 */
class BatteryExemptionIntentPolicyTest {

    @Test
    fun `the first attempt is the one-tap direct request`() {
        assertEquals(
            BatteryExemptionAttempt.DIRECT_REQUEST,
            BatteryExemptionIntentPolicy.firstAttempt(),
        )
    }

    @Test
    fun `a direct request that cannot start falls back to the settings list`() {
        assertEquals(
            BatteryExemptionAttempt.SETTINGS_LIST,
            BatteryExemptionIntentPolicy.attemptAfter(BatteryExemptionAttempt.DIRECT_REQUEST),
        )
    }

    @Test
    fun `the settings list is the last resort and nothing follows it`() {
        assertNull(BatteryExemptionIntentPolicy.attemptAfter(BatteryExemptionAttempt.SETTINGS_LIST))
    }

    @Test
    fun `walking the plan visits the dialog then the list then ends`() {
        val visited = mutableListOf<BatteryExemptionAttempt>()
        var attempt: BatteryExemptionAttempt? = BatteryExemptionIntentPolicy.firstAttempt()
        while (attempt != null) {
            visited += attempt
            attempt = BatteryExemptionIntentPolicy.attemptAfter(attempt)
        }
        assertEquals(
            listOf(BatteryExemptionAttempt.DIRECT_REQUEST, BatteryExemptionAttempt.SETTINGS_LIST),
            visited,
        )
    }
}
