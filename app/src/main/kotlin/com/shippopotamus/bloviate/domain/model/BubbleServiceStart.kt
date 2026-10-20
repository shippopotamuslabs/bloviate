package com.shippopotamus.bloviate.domain.model

/**
 * Where a bubble-service start came from (ENC-129). The origin decides
 * which foreground type the start may claim: Android 15 forbids
 * starting a microphone-typed foreground service from a boot receiver,
 * and a background restart is not guaranteed the eligibility an
 * in-app start has, so the start-type policy in
 * `BubbleServiceStartPolicy` keys off this.
 */
enum class BubbleServiceStartOrigin {

    /** The user started the bubble from the app (Setup switch, wizard test step). */
    USER,

    /** The boot receiver started the service after device boot. */
    BOOT,

    /** The system restarted the service after killing it (sticky restart, null intent). */
    RESTART,
}

/**
 * The foreground type the bubble service runs under, framework-free:
 * the service maps these onto the platform's `ServiceInfo` constants.
 * `MICROPHONE` is the only type allowed to capture audio; a service
 * running as [SPECIAL_USE] shows the bubble but cannot record yet.
 */
enum class BubbleForegroundType {
    MICROPHONE,
    SPECIAL_USE,
}
