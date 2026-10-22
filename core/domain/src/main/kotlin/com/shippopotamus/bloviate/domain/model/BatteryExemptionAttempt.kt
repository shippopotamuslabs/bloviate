package com.shippopotamus.bloviate.domain.model

/**
 * One way of putting the battery-exemption ask in front of the user
 * (ENC-139), framework-free: the presentation layer maps each attempt
 * onto the platform intent it names. The order the attempts are
 * tried in is `BatteryExemptionIntentPolicy`'s business.
 */
enum class BatteryExemptionAttempt {

    /**
     * The one-tap system dialog: the platform asks "allow this app
     * to run in the background?" in place, over the app, and the user
     * never leaves the flow — the grant Wispr Flow's onboarding uses.
     * Maps to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` carrying
     * this app's package URI.
     */
    DIRECT_REQUEST,

    /**
     * The battery-optimization settings list, where the user finds
     * Bloviate and sets it to Unrestricted by hand. Maps to
     * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. The fallback of
     * last resort: some OEM builds strip the direct-request dialog,
     * and this list is core Settings UI that is always there.
     */
    SETTINGS_LIST,
}
