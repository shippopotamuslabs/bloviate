package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.BubbleAppearance
import com.shippopotamus.bloviate.domain.model.BubbleShape
import com.shippopotamus.bloviate.domain.model.BubbleSize
import com.shippopotamus.bloviate.domain.model.BubbleTint
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreBubbleAppearanceStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreBubbleAppearanceStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("bubble-appearance-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreBubbleAppearanceStore(dataStore, storeScope)
    }

    @Test
    fun `absent record reads as the default appearance`() = runTest {
        assertEquals(BubbleAppearance.DEFAULT, store.appearance.value)
    }

    @Test
    fun `saving publishes and persists across store instances`() = runTest {
        val look = BubbleAppearance(
            opacity = 0.6f,
            size = BubbleSize.LARGE,
            shape = BubbleShape.CLOVER,
            tint = BubbleTint.WARNING,
        )

        store.save(look)

        assertEquals(look, store.appearance.value)

        val reread = DataStoreBubbleAppearanceStore(dataStore, storeScope)
        assertEquals(look, reread.appearance.value)
    }

    @Test
    fun `saving again overwrites the whole look`() = runTest {
        store.save(
            BubbleAppearance(size = BubbleSize.LARGE, shape = BubbleShape.ARCH),
        )
        store.save(
            BubbleAppearance(size = BubbleSize.SMALL, shape = BubbleShape.PILL, tint = BubbleTint.INFO),
        )

        assertEquals(
            BubbleAppearance(size = BubbleSize.SMALL, shape = BubbleShape.PILL, tint = BubbleTint.INFO),
            store.appearance.value,
        )
    }

    @Test
    fun `an unknown schema version invalidates the record to the default`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("bubble_appearance_schema_version")] = 99
            preferences[floatPreferencesKey("bubble_opacity")] = 0.5f
            preferences[stringPreferencesKey("bubble_size")] = "LARGE"
            preferences[stringPreferencesKey("bubble_shape")] = "ARCH"
            preferences[stringPreferencesKey("bubble_tint")] = "INFO"
        }

        // The stored look is not reinterpreted: an unknown version
        // invalidates the whole record to the default.
        assertEquals(BubbleAppearance.DEFAULT, store.appearance.value)
    }

    @Test
    fun `an unrecognized enum name invalidates only its own field`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("bubble_appearance_schema_version")] = 1
            preferences[floatPreferencesKey("bubble_opacity")] = 0.5f
            preferences[stringPreferencesKey("bubble_size")] = "LARGE"
            preferences[stringPreferencesKey("bubble_shape")] = "NOT_A_SHAPE"
            preferences[stringPreferencesKey("bubble_tint")] = "INFO"
        }

        // The removed silhouette falls back to its default; the rest of
        // the v1 record — including the surviving fields — still reads.
        assertEquals(
            BubbleAppearance(
                opacity = 0.5f,
                size = BubbleSize.LARGE,
                shape = BubbleShape.CIRCLE,
                tint = BubbleTint.INFO,
            ),
            store.appearance.value,
        )
    }

    @Test
    fun `a stored opacity outside the legal range is coerced on read`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("bubble_appearance_schema_version")] = 1
            preferences[floatPreferencesKey("bubble_opacity")] = 0.05f
        }

        assertEquals(BubbleAppearance.MIN_OPACITY, store.appearance.value.opacity)
    }

    @Test
    fun `saving an out-of-range opacity persists the coerced value`() = runTest {
        store.save(BubbleAppearance(opacity = 0.1f))

        assertEquals(BubbleAppearance.MIN_OPACITY, store.appearance.value.opacity)
        val reread = DataStoreBubbleAppearanceStore(dataStore, storeScope)
        assertEquals(BubbleAppearance.MIN_OPACITY, reread.appearance.value.opacity)
    }
}
