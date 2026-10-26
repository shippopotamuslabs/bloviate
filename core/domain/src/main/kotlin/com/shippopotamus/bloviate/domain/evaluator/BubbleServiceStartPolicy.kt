package com.shippopotamus.bloviate.domain.evaluator

import com.shippopotamus.bloviate.domain.model.BubbleForegroundType
import com.shippopotamus.bloviate.domain.model.BubbleServiceStartOrigin
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState

/**
 * The bubble service's start decisions (ENC-129), as pure functions:
 * which foreground type a start begins under, which type it may
 * promote to once the start's origin is known, whether the boot
 * receiver may start the service at all, and whether a start asks the
 * system for a sticky restart.
 *
 * The service always *begins* as [BubbleForegroundType.SPECIAL_USE]:
 * `onCreate` runs before the starting intent is delivered, so it
 * cannot know the start's origin, and `SPECIAL_USE` is the only type
 * legal in every context — critically at boot, where Android 15
 * throws `ForegroundServiceStartNotAllowedException` for a
 * microphone-typed start from a boot receiver. Promotion happens in
 * `onStartCommand`, where the origin is known: a user start is always
 * eligible (the app is in the foreground making it), a sticky restart
 * may be (the service attempts it under a guard and keeps
 * `SPECIAL_USE` if the platform refuses), and a boot start is never
 * eligible — it stays `SPECIAL_USE` until the next eligible moment,
 * the user's next in-app start.
 */
object BubbleServiceStartPolicy {

    /** The type every start begins under: legal at boot, in the background, anywhere. */
    fun initialType(): BubbleForegroundType = BubbleForegroundType.SPECIAL_USE

    /**
     * The type a start with [origin] may promote to once running, or
     * null when no promotion applies: boot starts never promote, and
     * without the microphone permission there is nothing to promote
     * to (the service already runs as [BubbleForegroundType.SPECIAL_USE],
     * and a microphone-typed start without the grant is refused by
     * the platform).
     */
    fun promotionType(
        origin: BubbleServiceStartOrigin,
        micPermissionGranted: Boolean,
    ): BubbleForegroundType? =
        when (origin) {
            BubbleServiceStartOrigin.BOOT -> null
            BubbleServiceStartOrigin.USER,
            BubbleServiceStartOrigin.RESTART,
            -> if (micPermissionGranted) BubbleForegroundType.MICROPHONE else null
        }

    /**
     * Whether the presence record allows an automatic start: the
     * resident-presence invariant only — switched on, and the user
     * has not explicitly stopped the bubble (a stop is honored
     * across reboots until the next in-app start). The setup-finished
     * precondition is the calling use case's, not this policy's.
     */
    fun shouldAutoStart(presence: ResidentPresenceState): Boolean =
        presence.autoStartEnabled && !presence.userStopped

    /**
     * Whether a start asks the system to restart the service after a
     * kill. The Setup off switch governs the whole resident posture,
     * stickiness included; a user-pressed Stop never reaches this
     * decision because stop tears the service down instead of
     * returning a start mode.
     */
    fun restartIsSticky(presence: ResidentPresenceState): Boolean = presence.autoStartEnabled
}
