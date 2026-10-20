package com.shippopotamus.bloviate.domain.model

/**
 * The bubble service's resident-presence record (ENC-129): whether the
 * service may make itself resident — start at boot and restart after a
 * system kill — and whether the user has explicitly stopped it.
 *
 * [autoStartEnabled] is the Setup off switch for the whole resident
 * posture; it defaults on because presence is the product behavior
 * once setup is complete, and the switch is how a user opts out.
 * [userStopped] is the user's explicit Stop (the notification action
 * or the Setup bubble switch): sticky restart and boot start both
 * honor it — the service stays down until the user next starts it
 * from the app, which clears the flag. A system kill sets neither
 * flag; that asymmetry is what lets restart logic tell a kill from
 * a stop. Persisted as a versioned DataStore record (see
 * `DataStoreResidentPresenceStore`).
 */
data class ResidentPresenceState(
    val autoStartEnabled: Boolean = true,
    val userStopped: Boolean = false,
) {
    companion object {
        /** No record on disk yet: presence allowed, nothing stopped. */
        val DEFAULT: ResidentPresenceState = ResidentPresenceState()
    }
}
