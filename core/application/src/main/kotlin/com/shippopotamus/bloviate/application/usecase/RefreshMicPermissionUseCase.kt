package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.MicPermissionPort
import javax.inject.Inject

/**
 * Re-publishes the live `RECORD_AUDIO` grant to state observers (the
 * recording session's availability). Called at the moments the grant can
 * change: screen resume and permission-request result.
 */
class RefreshMicPermissionUseCase @Inject constructor(
    private val micPermissionPort: MicPermissionPort,
) {

    operator fun invoke() {
        micPermissionPort.refresh()
    }
}
