package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.StylePreferencePort
import javax.inject.Inject

/** Persists the user's writing-style choice; the next dictation uses it. */
class SelectStyleUseCase @Inject constructor(
    private val stylePreference: StylePreferencePort,
) {

    suspend operator fun invoke(styleId: String) {
        stylePreference.selectStyle(styleId)
    }
}
