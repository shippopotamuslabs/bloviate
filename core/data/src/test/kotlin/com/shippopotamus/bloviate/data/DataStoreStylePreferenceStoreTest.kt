package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreStylePreferenceStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreStylePreferenceStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("style-preference-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreStylePreferenceStore(dataStore, storeScope)
    }

    @Test
    fun `default selection is the catalog default`() = runTest {
        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `selecting a style publishes and persists across store instances`() = runTest {
        store.selectStyle("seneca")

        assertEquals("seneca", store.selectedStyleId.value)

        val reread = DataStoreStylePreferenceStore(dataStore, storeScope)
        assertEquals("seneca", reread.selectedStyleId.value)
    }

    @Test
    fun `selecting another style again round trips`() = runTest {
        store.selectStyle("seneca")
        store.selectStyle("lowercase-casual")

        assertEquals("lowercase-casual", store.selectedStyleId.value)
    }

    @Test
    fun `an unrecognized persisted id falls back to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 1
            preferences[stringPreferencesKey("selected_style_id")] = "NOT_A_STYLE"
        }

        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `a v1 custom id still normalizes to the default`() = runTest {
        // v1 keeps its exact v1 meaning: the static catalog knows no
        // custom ids, so a v1-stored custom id invalidates to the default.
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 1
            preferences[stringPreferencesKey("selected_style_id")] = "custom-my-voice"
        }

        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `a v1 known id still reads through the static catalog`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 1
            preferences[stringPreferencesKey("selected_style_id")] = "seneca"
        }

        assertEquals("seneca", store.selectedStyleId.value)
    }

    @Test
    fun `a v2 custom id survives the read verbatim`() = runTest {
        store.selectStyle("custom-my-voice")

        assertEquals("custom-my-voice", store.selectedStyleId.value)

        val reread = DataStoreStylePreferenceStore(dataStore, storeScope)
        assertEquals("custom-my-voice", reread.selectedStyleId.value)
    }

    @Test
    fun `a v2 blank or missing id reads as the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 2
            preferences[stringPreferencesKey("selected_style_id")] = "  "
        }
        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)

        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("selected_style_id"))
        }
        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `an unknown schema version invalidates the record to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 99
            preferences[stringPreferencesKey("selected_style_id")] = "seneca"
        }

        // The stored seneca is not reinterpreted: an unknown version
        // invalidates the whole record to the default.
        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `a version past v2 invalidates the record to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("style_schema_version")] = 3
            preferences[stringPreferencesKey("selected_style_id")] = "custom-my-voice"
        }

        assertEquals(StyleCatalog.DEFAULT.id, store.selectedStyleId.value)
    }

    @Test
    fun `selecting a style stamps schema version 2 with the raw id`() = runTest {
        store.selectStyle("custom-my-voice")

        val snapshot = dataStore.data.first()
        assertEquals(2, snapshot[intPreferencesKey("style_schema_version")])
        assertEquals("custom-my-voice", snapshot[stringPreferencesKey("selected_style_id")])
    }
}
