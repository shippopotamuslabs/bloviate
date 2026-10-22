package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Base64

class EncryptedApiKeyStoreTest {

    /**
     * Stands in for the Android Keystore cipher: reversibly scrambles with
     * Base64 (whose alphabet carries no separator dots) so the serialize /
     * parse round trip is exercised exactly like production.
     */
    private class FakeKeyCipher : KeyCipher {

        override fun encrypt(plain: String): KeyCipherText =
            KeyCipherText(
                ivBase64 = "fake-iv",
                cipherBase64 = Base64.getEncoder().encodeToString(plain.reversed().toByteArray()),
            )

        override fun decrypt(payload: KeyCipherText): String =
            String(Base64.getDecoder().decode(payload.cipherBase64)).reversed()
    }

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: EncryptedApiKeyStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("api-key-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = EncryptedApiKeyStore(dataStore, FakeKeyCipher(), storeScope)
    }

    @Test
    fun `no stored key reads as absent`() = runTest {
        assertFalse(store.hasKey.value)
        assertNull(store.currentKey())
    }

    @Test
    fun `a saved key round-trips and publishes presence`() = runTest {
        store.saveKey("sk-test-key")

        assertTrue(store.hasKey.value)
        assertEquals("sk-test-key", store.currentKey())
    }

    @Test
    fun `the persisted record never contains the plaintext`() = runTest {
        store.saveKey("sk-test-key")

        val raw = dataStore.data.first()[stringPreferencesKey("openrouter_key_ciphertext")].orEmpty()
        assertFalse(raw.isBlank())
        assertFalse(raw.contains("sk-test-key"))
    }

    @Test
    fun `clearing removes the key and presence`() = runTest {
        store.saveKey("sk-test-key")
        store.clearKey()

        assertFalse(store.hasKey.value)
        assertNull(store.currentKey())
    }

    @Test
    fun `an unknown schema version reads as no key`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("api_key_schema_version")] = 99
            preferences[stringPreferencesKey("openrouter_key_ciphertext")] = "fake-iv.e30="
        }

        assertFalse(store.hasKey.value)
        assertNull(store.currentKey())
    }
}
