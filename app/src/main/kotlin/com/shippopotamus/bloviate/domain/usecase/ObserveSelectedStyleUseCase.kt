package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.StylePreferencePort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's selected writing-style id. */
class ObserveSelectedStyleUseCase @Inject constructor(
    private val stylePreference: StylePreferencePort,
) {

    operator fun invoke(): StateFlow<String> = stylePreference.selectedStyleId
}
