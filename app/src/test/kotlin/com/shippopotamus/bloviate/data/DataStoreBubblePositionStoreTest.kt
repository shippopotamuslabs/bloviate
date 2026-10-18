package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.shippopotamus.bloviate.domain.model.BubblePosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreBubblePositionStoreTest {

    // Unconfined so DataStore work completes eagerly inside runTest — the
    // deterministic pattern from UNIT_TESTING.md §3.3, never a delay() poll.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreBubblePositionStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("bubble-position-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreBubblePositionStore(dataStore)
    }

    @Test
    fun `no saved position emits null`() = runTest {
        assertNull(store.position.first())
    }

    @Test
    fun `saved position round trips`() = runTest {
        val saved = BubblePosition(xFraction = 0.25f, yFraction = 0.75f)

        store.save(saved)

        assertEquals(saved, store.position.first())
    }

    @Test
    fun `saving twice keeps the latest position`() = runTest {
        store.save(BubblePosition(xFraction = 0.1f, yFraction = 0.1f))
        val latest = BubblePosition(xFraction = 0.9f, yFraction = 0.2f)
        store.save(latest)

        assertEquals(latest, store.position.first())
    }

    @Test
    fun `a record written by an unknown schema version is ignored, not reinterpreted`() = runTest {
        // Writes the v1 key layout under a future version number, simulating
        // a downgrade: the store must treat it as "no saved position".
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("bubble_position_schema_version")] = UNKNOWN_SCHEMA_VERSION
            preferences[floatPreferencesKey("bubble_position_x_fraction")] = 0.5f
            preferences[floatPreferencesKey("bubble_position_y_fraction")] = 0.5f
        }

        assertNull(store.position.first())
    }

    private companion object {
        const val UNKNOWN_SCHEMA_VERSION: Int = 99
    }
}
