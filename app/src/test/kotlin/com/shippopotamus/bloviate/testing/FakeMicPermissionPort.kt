package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-written fake of [MicPermissionPort]. [liveGranted] is what the
 * "platform" reports; the published state only follows it on [refresh],
 * mirroring the real implementation.
 */
class FakeMicPermissionPort : MicPermissionPort {

    var liveGranted: Boolean = false

    var refreshCount: Int = 0
        private set

    private val mutableGranted = MutableStateFlow(false)

    override val isGranted: StateFlow<Boolean> = mutableGranted.asStateFlow()

    override fun isGrantedNow(): Boolean = liveGranted

    override fun refresh() {
        refreshCount++
        mutableGranted.value = liveGranted
    }

    /** Grants/revokes at the platform level AND publishes immediately. */
    fun setGranted(granted: Boolean) {
        liveGranted = granted
        mutableGranted.value = granted
    }
}
