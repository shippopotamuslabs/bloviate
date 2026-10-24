package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import javax.inject.Inject

/** Signs out of Firebase and clears Credential Manager state (ENC-197). */
class SignOutUseCase @Inject constructor(
    private val authSession: AuthSessionPort,
) {

    suspend operator fun invoke(): Result<Unit> = authSession.signOut()
}
