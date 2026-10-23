package com.shippopotamus.bloviate.application.port

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's own OpenRouter API key (ENC-73, BYOK). The plaintext lives in
 * memory only long enough to use; at rest it is ciphertext in DataStore
 * (see `EncryptedApiKeyStore`). The key is never logged, never lands in
 * `BuildConfig`, and never appears in UI state — the UI only sees the
 * [hasKey] boolean and a transient draft field.
 */
interface ApiKeyPort {

    /** Whether a key is currently stored. */
    val hasKey: StateFlow<Boolean>

    /** Encrypts and persists [key]; blank keys are rejected by the caller. */
    suspend fun saveKey(key: String)

    /** Removes any stored key. */
    suspend fun clearKey()

    /** The stored key for the data layer's transport call only — never log it. */
    suspend fun currentKey(): String?
}
