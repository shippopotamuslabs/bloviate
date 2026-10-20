package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.port.OnboardingPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hand-written fake of [OnboardingPort]: an in-memory StateFlow plus a save record. */
class FakeOnboardingPort : OnboardingPort {

    private val mutableProgress = MutableStateFlow(OnboardingProgress.NOT_STARTED)

    override val progress: StateFlow<OnboardingProgress> = mutableProgress.asStateFlow()

    val savedProgresses = mutableListOf<OnboardingProgress>()

    var saveCallCount: Int = 0
        private set

    override suspend fun save(progress: OnboardingProgress) {
        saveCallCount++
        savedProgresses += progress
        mutableProgress.value = progress
    }

    /** Publishes progress as if another writer (the store) had saved it. */
    fun publish(progress: OnboardingProgress) {
        mutableProgress.value = progress
    }
}
