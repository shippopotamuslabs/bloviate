package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import javax.inject.Inject

/**
 * Records the user's explicit stop of the bubble from inside the app
 * (the Setup switch, ENC-129): boot start and sticky restart must
 * honor it — the bubble stays down until the next explicit start.
 * The notification's Stop action records the same fact from inside
 * the service, which writes the record through the port directly.
 */
class RecordBubbleUserStopUseCase @Inject constructor(
    private val residentPresencePort: ResidentPresencePort,
) {

    suspend operator fun invoke() {
        val current = residentPresencePort.state.value
        if (!current.userStopped) {
            residentPresencePort.save(current.copy(userStopped = true))
        }
    }
}
