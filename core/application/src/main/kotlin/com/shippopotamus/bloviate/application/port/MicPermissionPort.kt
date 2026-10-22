package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The `RECORD_AUDIO` runtime permission, published as state so the recording
 * session's availability can react when the user grants or revokes it.
 * Android exposes no permission-change callback, so [refresh] re-reads the
 * live grant at the moments it can change (screen resume, permission-request
 * result, service start, session start).
 */
interface MicPermissionPort {

    /** Last refreshed grant state. */
    val isGranted: StateFlow<Boolean>

    /** Live grant check against the platform, without publishing. */
    fun isGrantedNow(): Boolean

    /** Re-reads the live grant and publishes it to [isGranted] if it changed. */
    fun refresh()
}
