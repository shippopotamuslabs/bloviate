package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import javax.inject.Inject

/** Persists the user's dictation-engine choice; the next dictation uses it. */
class SelectEngineUseCase @Inject constructor(
    private val enginePreference: EnginePreferencePort,
) {

    suspend operator fun invoke(engine: DictationEngine) {
        enginePreference.selectEngine(engine)
    }
}
