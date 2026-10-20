package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The battery-exemption attempt plan (ENC-139, escalation pinned by
 * ENC-141): a fresh session leads with the one-tap system dialog; a
 * failed start of any kind advances to the settings list, which is
 * the last resort; and once a direct attempt has fired this session,
 * the next tap escalates straight to the list so the button is never
 * a dead end twice.
 */
class BatteryExemptionIntentPolicyTest {

    @Test
    fun `a fresh session leads with the one-tap direct request`() {
        assertEquals(
            BatteryExemptionAttempt.DIRECT_REQUEST,
            BatteryExemptionIntentPolicy.firstAttempt(directAlreadyTried = false),
        )
    }

    @Test
    fun `a direct attempt already fired this session escalates straight to the settings list`() {
        assertEquals(
            BatteryExemptionAttempt.SETTINGS_LIST,
            BatteryExemptionIntentPolicy.firstAttempt(directAlreadyTried = true),
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
    fun `walking a fresh session's plan visits the dialog then the list then ends`() {
        val visited = mutableListOf<BatteryExemptionAttempt>()
        var attempt: BatteryExemptionAttempt? =
            BatteryExemptionIntentPolicy.firstAttempt(directAlreadyTried = false)
        while (attempt != null) {
            visited += attempt
            attempt = BatteryExemptionIntentPolicy.attemptAfter(attempt)
        }
        assertEquals(
            listOf(BatteryExemptionAttempt.DIRECT_REQUEST, BatteryExemptionAttempt.SETTINGS_LIST),
            visited,
        )
    }

    @Test
    fun `walking an escalated session's plan visits only the list then ends`() {
        val visited = mutableListOf<BatteryExemptionAttempt>()
        var attempt: BatteryExemptionAttempt? =
            BatteryExemptionIntentPolicy.firstAttempt(directAlreadyTried = true)
        while (attempt != null) {
            visited += attempt
            attempt = BatteryExemptionIntentPolicy.attemptAfter(attempt)
        }
        assertEquals(listOf(BatteryExemptionAttempt.SETTINGS_LIST), visited)
    }
}
