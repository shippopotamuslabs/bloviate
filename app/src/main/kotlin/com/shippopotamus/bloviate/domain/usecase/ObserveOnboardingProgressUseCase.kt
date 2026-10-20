package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.port.OnboardingPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's place in the first-run wizard (resume step + finished flag). */
class ObserveOnboardingProgressUseCase @Inject constructor(
    private val onboarding: OnboardingPort,
) {

    operator fun invoke(): StateFlow<OnboardingProgress> = onboarding.progress
}
