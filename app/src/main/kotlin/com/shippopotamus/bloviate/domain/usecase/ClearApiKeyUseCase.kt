package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.ApiKeyPort
import javax.inject.Inject

/** Removes the stored OpenRouter key; LLM styles fall back to local text. */
class ClearApiKeyUseCase @Inject constructor(
    private val apiKeys: ApiKeyPort,
) {

    suspend operator fun invoke() {
        apiKeys.clearKey()
    }
}
