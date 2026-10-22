package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import kotlinx.coroutines.flow.StateFlow

/**
 * The bubble service's persisted resident-presence record (ENC-129):
 * whether the service may start itself at boot and restart after a
 * system kill, and whether the user has explicitly stopped it. Read
 * by the boot receiver and the service; written by the Setup switch,
 * the user's explicit starts, and the notification's Stop action.
 */
interface ResidentPresencePort {

    /** The current record; [ResidentPresenceState.DEFAULT] until first saved. */
    val state: StateFlow<ResidentPresenceState>

    /** Persists [state] as the whole record. */
    suspend fun save(state: ResidentPresenceState)
}
