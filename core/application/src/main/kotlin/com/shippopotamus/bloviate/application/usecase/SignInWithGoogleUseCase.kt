package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.UserProfile
import javax.inject.Inject

/** Runs the Credential Manager Google sign-in flow (ENC-197). */
class SignInWithGoogleUseCase @Inject constructor(
    private val authSession: AuthSessionPort,
) {

    suspend operator fun invoke(): Result<UserProfile> = authSession.signInWithGoogle()
}
