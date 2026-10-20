package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.port.OnboardingPort
import javax.inject.Inject

/** Persists the user's place in the first-run wizard; the shell reacts to the next emission. */
class SaveOnboardingProgressUseCase @Inject constructor(
    private val onboarding: OnboardingPort,
) {

    suspend operator fun invoke(progress: OnboardingProgress) {
        onboarding.save(progress)
    }
}
