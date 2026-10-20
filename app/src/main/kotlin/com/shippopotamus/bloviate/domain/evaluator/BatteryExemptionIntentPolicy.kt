package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BatteryExemptionAttempt

/**
 * The battery-exemption ask, as a pure attempt plan (ENC-139).
 *
 * ENC-129 sent the user to the battery-optimization settings list to
 * find Bloviate and set it to Unrestricted by hand. The platform also
 * offers a one-tap dialog — an intent carrying our package URI that
 * asks the question in place — and that is what the ask tries first:
 * tap the button, the system dialog appears, Allow, done, without
 * leaving the wizard or Setup. The list remains as the fallback for
 * OEM builds that strip the direct-request intent (its
 * `ActivityNotFoundException` is the signal to advance the plan), so
 * the step can never dead-end.
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

    /** The first attempt is always the one-tap dialog. */
    fun firstAttempt(): BatteryExemptionAttempt = BatteryExemptionAttempt.DIRECT_REQUEST

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
