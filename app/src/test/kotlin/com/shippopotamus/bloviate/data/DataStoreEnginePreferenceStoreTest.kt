package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.DictationEngine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreEnginePreferenceStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreEnginePreferenceStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("engine-preference-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreEnginePreferenceStore(dataStore, storeScope)
    }

    @Test
    fun `default selection is whisper`() = runTest {
        assertEquals(DictationEngine.WHISPER, store.selectedEngine.value)
    }

    @Test
    fun `selecting parakeet publishes and persists across store instances`() = runTest {
        store.selectEngine(DictationEngine.PARAKEET)

        assertEquals(DictationEngine.PARAKEET, store.selectedEngine.value)

        val reread = DataStoreEnginePreferenceStore(dataStore, storeScope)
        assertEquals(DictationEngine.PARAKEET, reread.selectedEngine.value)
    }

    @Test
    fun `selecting whisper again round trips`() = runTest {
        store.selectEngine(DictationEngine.PARAKEET)
        store.selectEngine(DictationEngine.WHISPER)

        assertEquals(DictationEngine.WHISPER, store.selectedEngine.value)
    }

    @Test
    fun `an unrecognized persisted value falls back to whisper`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("engine_preference_schema_version")] = 1
            preferences[stringPreferencesKey("dictation_engine")] = "NOT_AN_ENGINE"
        }

        assertEquals(DictationEngine.WHISPER, store.selectedEngine.value)
    }

    @Test
    fun `an unknown schema version invalidates the record to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("engine_preference_schema_version")] = 99
            preferences[stringPreferencesKey("dictation_engine")] = "PARAKEET"
        }

        assertEquals(DictationEngine.WHISPER, store.selectedEngine.value)
    }
}
