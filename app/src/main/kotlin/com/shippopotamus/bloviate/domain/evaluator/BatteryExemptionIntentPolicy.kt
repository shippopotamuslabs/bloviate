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
 * Session escalation (ENC-141): if a direct attempt already fired
 * this session and the exemption still reads not-granted when the
 * user taps again, the plan starts at the settings list instead.
 * An honest Deny deserves the same escalation as a swallowed
 * dialog: the button must never be a dead end twice. (The dead
 * tap that motivated the escalation was re-diagnosed by ENC-143 —
 * the manifest lacked the permission the direct intent requires —
 * but the escalation stays as backstop for builds that genuinely
 * swallow the dialog.)
 *
 * Policy flag, recorded rather than obeyed: the direct-request intent
 * is Play-policy-restricted to core-functionality exemptions with a
 * declaration at submission. Bloviate is sideload-first (the Play
 * decision is deferred), and Wispr Flow ships this same dialog on
 * Play in this category, so the flag rides the launch checklist —
 * it does not degrade the pre-Play experience back to the list.
 *
 * Manifest requirement (ENC-143): the direct intent is honored
 * only because the manifest declares
 * `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — a
 * normal install-time permission with no runtime prompt. Without
 * the declaration the system refuses the dialog on dispatch and
 * the button reads as dead; that missing declaration, not the
 * device, was the ENC-139/ENC-141 dead tap. The settings-list
 * intent needs no declaration.
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
