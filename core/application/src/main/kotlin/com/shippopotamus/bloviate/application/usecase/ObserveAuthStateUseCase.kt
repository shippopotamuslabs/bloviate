package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the launcher gate state: sign-in screen or app shell (ENC-197). */
class ObserveAuthStateUseCase @Inject constructor(
    private val authSession: AuthSessionPort,
) {

    operator fun invoke(): StateFlow<AuthState> = authSession.authState
}
