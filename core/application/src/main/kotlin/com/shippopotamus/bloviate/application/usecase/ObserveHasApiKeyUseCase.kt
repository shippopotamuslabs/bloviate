package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.ApiKeyPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams whether an OpenRouter key is stored (never the key itself). */
class ObserveHasApiKeyUseCase @Inject constructor(
    private val apiKeys: ApiKeyPort,
) {

    operator fun invoke(): StateFlow<Boolean> = apiKeys.hasKey
}
