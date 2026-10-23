package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.application.port.EnginePreferencePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's selected dictation engine. */
class ObserveEnginePreferenceUseCase @Inject constructor(
    private val enginePreference: EnginePreferencePort,
) {

    operator fun invoke(): StateFlow<DictationEngine> = enginePreference.selectedEngine
}
