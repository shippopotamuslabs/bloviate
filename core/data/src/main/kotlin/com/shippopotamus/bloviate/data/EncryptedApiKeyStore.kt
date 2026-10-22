package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.application.port.ApiKeyPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ApiKeyPort] backed by the shared preferences DataStore (ENC-73).
 *
 * Persisted format (schema v1): a version int plus one Base64
 * `iv.ciphertext` string from [KeyCipher]. The plaintext key is never
 * persisted and never logged. A missing or unknown version, or an
 * unparsable payload, reads as no key — an explicit invalidation, never a
 * silent reinterpretation — and the next save rewrites the record at v1.
 */
@Singleton
class EncryptedApiKeyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeyCipher,
    scope: CoroutineScope,
) : ApiKeyPort {

    override val hasKey: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.hasStoredKey() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    override suspend fun saveKey(key: String) {
        val payload = cipher.encrypt(key).serialize()
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_CIPHERTEXT] = payload
        }
    }

    override suspend fun clearKey() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SCHEMA_VERSION)
            preferences.remove(KEY_CIPHERTEXT)
        }
    }

    override suspend fun currentKey(): String? {
        val payload = dataStore.data.first().toCipherText() ?: return null
        return cipher.decrypt(payload)
    }

    private fun Preferences.hasStoredKey(): Boolean = toCipherText() != null

    private fun Preferences.toCipherText(): KeyCipherText? {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return null
        val serialized = this[KEY_CIPHERTEXT] ?: return null
        return KeyCipherText.parse(serialized)
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("api_key_schema_version")
        val KEY_CIPHERTEXT = stringPreferencesKey("openrouter_key_ciphertext")
    }
}
