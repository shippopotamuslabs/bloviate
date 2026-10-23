package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import javax.inject.Inject

/** Persists the user's dictation-engine choice; the next dictation uses it. */
class SelectEngineUseCase @Inject constructor(
    private val enginePreference: EnginePreferencePort,
) {

    suspend operator fun invoke(engine: DictationEngine) {
        enginePreference.selectEngine(engine)
    }
}
