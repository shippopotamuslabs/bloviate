package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.MicPermissionPort
import javax.inject.Inject

/** Live read of the `RECORD_AUDIO` grant (does not rely on the last refresh). */
class IsMicPermissionGrantedUseCase @Inject constructor(
    private val micPermissionPort: MicPermissionPort,
) {

    operator fun invoke(): Boolean = micPermissionPort.isGrantedNow()
}
