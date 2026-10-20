package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import com.shippopotamus.bloviate.domain.port.ResidentPresencePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the bubble service's resident-presence record (ENC-129). */
class ObserveResidentPresenceUseCase @Inject constructor(
    private val residentPresencePort: ResidentPresencePort,
) {

    operator fun invoke(): StateFlow<ResidentPresenceState> = residentPresencePort.state
}
