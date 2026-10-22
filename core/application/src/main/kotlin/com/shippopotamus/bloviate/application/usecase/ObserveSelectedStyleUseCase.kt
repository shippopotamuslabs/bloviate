package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.StylePreferencePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's selected writing-style id. */
class ObserveSelectedStyleUseCase @Inject constructor(
    private val stylePreference: StylePreferencePort,
) {

    operator fun invoke(): StateFlow<String> = stylePreference.selectedStyleId
}
