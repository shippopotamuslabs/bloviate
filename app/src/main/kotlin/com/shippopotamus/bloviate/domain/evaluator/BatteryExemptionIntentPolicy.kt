package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt

/**
 * The battery-exemption ask, as a pure attempt plan (ENC-139,
 * hardened by ENC-141).
 *
 * ENC-129 sent the user to the battery-optimization settings list to
 * find Bloviate and set it to Unrestricted by hand. The platform also
 * offers a one-tap dialog — an intent carrying our package URI that
 * asks the question in place — and that is what the ask tries first:
 * tap the button, the system dialog appears, Allow, done, without
 * leaving the wizard or Setup. The list remains as the fallback for
 * OEM builds that defeat the direct-request intent (any failure to
 * start it is the signal to advance the plan), so the step can never
 * dead-end.
 *
 * Session escalation (ENC-141): on Sean's Motorola the direct intent
 * starts without throwing yet shows nothing — a "successful" start
 * into an OEM no-op — so exception-driven fallback alone left the
 * button a dead tap. If a direct attempt already fired this session
 * and the exemption still reads not-granted when the user taps
 * again, the plan starts at the settings list instead. An honest
 * Deny deserves the same escalation as a swallowed dialog: the
 * button must never be a dead end twice.
 *
 * Policy flag, recorded rather than obeyed: the direct-request intent
 * is Play-policy-restricted to core-functionality exemptions with a
 * declaration at submission. Bloviate is sideload-first (the Play
 * decision is deferred), and Wispr Flow ships this same dialog on
 * Play in this category, so the flag rides the launch checklist —
 * it does not degrade the pre-Play experience back to the list.
 * Firing either intent needs no manifest permission.
 */
object BatteryExemptionIntentPolicy {

    /**
     * The first attempt of a tap. A fresh session leads with the
     * one-tap dialog; once a direct attempt has already fired this
     * session ([directAlreadyTried]) the dialog has had its chance —
     * it either no-opped or the user declined — so the plan escalates
     * straight to the settings list.
     */
    fun firstAttempt(directAlreadyTried: Boolean): BatteryExemptionAttempt =
        if (directAlreadyTried) {
            BatteryExemptionAttempt.SETTINGS_LIST
        } else {
            BatteryExemptionAttempt.DIRECT_REQUEST
        }

    /**
     * The attempt to make after [failed] could not be started, or
     * null when the plan is exhausted: the settings list is the last
     * resort, and nothing follows it.
     */
    fun attemptAfter(failed: BatteryExemptionAttempt): BatteryExemptionAttempt? =
        when (failed) {
            BatteryExemptionAttempt.DIRECT_REQUEST -> BatteryExemptionAttempt.SETTINGS_LIST
            BatteryExemptionAttempt.SETTINGS_LIST -> null
        }
}
