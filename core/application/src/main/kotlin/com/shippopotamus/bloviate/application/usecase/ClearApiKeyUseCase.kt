package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.ApiKeyPort
import javax.inject.Inject

/** Removes the stored OpenRouter key; LLM styles fall back to local text. */
class ClearApiKeyUseCase @Inject constructor(
    private val apiKeys: ApiKeyPort,
) {

    suspend operator fun invoke() {
        apiKeys.clearKey()
    }
}
