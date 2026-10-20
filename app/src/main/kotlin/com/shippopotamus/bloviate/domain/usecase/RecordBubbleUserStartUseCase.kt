package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.ResidentPresencePort
import javax.inject.Inject

/**
 * Records an explicit in-app start of the bubble (ENC-129): the user
 * asking for the bubble again is what lifts a previous Stop — from
 * here, boot start and sticky restart may resume it again.
 */
class RecordBubbleUserStartUseCase @Inject constructor(
    private val residentPresencePort: ResidentPresencePort,
) {

    suspend operator fun invoke() {
        val current = residentPresencePort.state.value
        if (current.userStopped) {
            residentPresencePort.save(current.copy(userStopped = false))
        }
    }
}
