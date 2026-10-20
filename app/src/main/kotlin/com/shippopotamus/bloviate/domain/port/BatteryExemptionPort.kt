package com.shippopotamus.bloviate.domain.port

/**
 * Whether Android's battery optimization leaves Bloviate alone
 * (ENC-129). An exempt app is what Wispr Flow's onboarding asks for
 * and what Bloviate never did: without the exemption the system may
 * throttle or kill the watcher and bubble services to save power,
 * which reads to the user as dictation that randomly stops working.
 */
interface BatteryExemptionPort {

    /** True while the app is on the battery-optimization exemption list. */
    fun isIgnoringBatteryOptimizations(): Boolean
}
