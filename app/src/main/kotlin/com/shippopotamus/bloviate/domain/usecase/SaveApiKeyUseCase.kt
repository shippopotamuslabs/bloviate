package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.port.ApiKeyPort
import javax.inject.Inject

/** Persists the user's own OpenRouter key (ENC-73, BYOK); blanks are ignored. */
class SaveApiKeyUseCase @Inject constructor(
    private val apiKeys: ApiKeyPort,
) {

    suspend operator fun invoke(key: String) {
        if (key.isBlank()) return
        apiKeys.saveKey(key.trim())
    }
}
