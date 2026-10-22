package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shippopotamus.bloviate.domain.model.ResidentPresenceState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreResidentPresenceStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreResidentPresenceStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("resident-presence-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreResidentPresenceStore(dataStore, storeScope)
    }

    @Test
    fun `absent record reads as the default - presence on, nothing stopped`() = runTest {
        assertEquals(ResidentPresenceState.DEFAULT, store.state.value)
    }

    @Test
    fun `saving publishes and persists across store instances`() = runTest {
        val presence = ResidentPresenceState(autoStartEnabled = false, userStopped = true)

        store.save(presence)

        assertEquals(presence, store.state.value)

        val reread = DataStoreResidentPresenceStore(dataStore, storeScope)
        assertEquals(presence, reread.state.value)
    }

    @Test
    fun `saving again overwrites the whole record`() = runTest {
        store.save(ResidentPresenceState(autoStartEnabled = false, userStopped = true))
        store.save(ResidentPresenceState(autoStartEnabled = true, userStopped = false))

        assertEquals(
            ResidentPresenceState(autoStartEnabled = true, userStopped = false),
            store.state.value,
        )
    }

    @Test
    fun `an unknown schema version invalidates the record to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("resident_presence_schema_version")] = 99
            preferences[booleanPreferencesKey("resident_presence_auto_start_enabled")] = false
            preferences[booleanPreferencesKey("resident_presence_user_stopped")] = true
        }

        val reread = DataStoreResidentPresenceStore(dataStore, storeScope)

        assertEquals(ResidentPresenceState.DEFAULT, reread.state.value)
    }

    @Test
    fun `a missing auto-start flag within v1 reads as on and keeps the stop flag`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("resident_presence_schema_version")] = 1
            preferences[booleanPreferencesKey("resident_presence_user_stopped")] = true
        }

        val reread = DataStoreResidentPresenceStore(dataStore, storeScope)

        assertEquals(
            ResidentPresenceState(autoStartEnabled = true, userStopped = true),
            reread.state.value,
        )
    }

    @Test
    fun `a missing stopped flag within v1 reads as not stopped and keeps the switch`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("resident_presence_schema_version")] = 1
            preferences[booleanPreferencesKey("resident_presence_auto_start_enabled")] = false
        }

        val reread = DataStoreResidentPresenceStore(dataStore, storeScope)

        assertEquals(
            ResidentPresenceState(autoStartEnabled = false, userStopped = false),
            reread.state.value,
        )
    }
}
