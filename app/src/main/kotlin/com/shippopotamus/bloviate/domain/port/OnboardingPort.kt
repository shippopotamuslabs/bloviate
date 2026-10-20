package com.shippopotamus.bloviate.domain.port

import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted place in the first-run wizard (ENC-67). The
 * app shell reads this to decide whether the wizard opens on launch,
 * and the wizard writes it as the user moves — so a walk interrupted
 * by a settings detour, a process death, or a skipped step resumes
 * where it left off instead of starting over.
 */
interface OnboardingPort {

    /** The current progress; [OnboardingProgress.NOT_STARTED] until first saved. */
    val progress: StateFlow<OnboardingProgress>

    /** Persists [progress] as the user's place in the walk. */
    suspend fun save(progress: OnboardingProgress)
}
