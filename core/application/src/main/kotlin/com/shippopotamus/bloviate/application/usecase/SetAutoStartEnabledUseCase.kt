package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.ResidentPresencePort
import javax.inject.Inject

/**
 * Persists the Setup "start on its own" switch (ENC-129): the whole
 * resident posture — boot start and sticky restart — follows this one
 * flag, read from the record's current value so the user's stop flag
 * is never clobbered by a switch flip.
 */
class SetAutoStartEnabledUseCase @Inject constructor(
    private val residentPresencePort: ResidentPresencePort,
) {

    suspend operator fun invoke(enabled: Boolean) {
        residentPresencePort.save(
            residentPresencePort.state.value.copy(autoStartEnabled = enabled),
        )
    }
}
