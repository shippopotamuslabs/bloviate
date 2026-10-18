package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.DictationEngine
import com.shippopotamus.bloviate.domain.port.EnginePreferencePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's selected dictation engine. */
class ObserveEnginePreferenceUseCase @Inject constructor(
    private val enginePreference: EnginePreferencePort,
) {

    operator fun invoke(): StateFlow<DictationEngine> = enginePreference.selectedEngine
}
