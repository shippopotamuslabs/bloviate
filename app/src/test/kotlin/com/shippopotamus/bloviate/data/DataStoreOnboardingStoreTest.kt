package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.OnboardingStep
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DataStoreOnboardingStoreTest {

    // Unconfined so DataStore work and the stateIn chain complete eagerly
    // inside runTest — the deterministic pattern from UNIT_TESTING.md §3.3.
    private val dataStoreScope = TestScope(UnconfinedTestDispatcher())
    private val storeScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DataStoreOnboardingStore

    @BeforeEach
    fun setup() {
        val file = Files.createTempFile("onboarding-test", ".preferences_pb").toFile()
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { file },
        )
        store = DataStoreOnboardingStore(dataStore, storeScope)
    }

    @Test
    fun `absent record reads as not started`() = runTest {
        assertEquals(OnboardingProgress.NOT_STARTED, store.progress.value)
    }

    @Test
    fun `saving publishes and persists across store instances`() = runTest {
        val progress = OnboardingProgress(step = OnboardingStep.MODEL, finished = false)

        store.save(progress)

        assertEquals(progress, store.progress.value)

        val reread = DataStoreOnboardingStore(dataStore, storeScope)
        assertEquals(progress, reread.progress.value)
    }

    @Test
    fun `saving again overwrites the whole record`() = runTest {
        store.save(OnboardingProgress(step = OnboardingStep.TEST, finished = false))
        store.save(OnboardingProgress(step = OnboardingStep.OVERLAY, finished = true))

        assertEquals(
            OnboardingProgress(step = OnboardingStep.OVERLAY, finished = true),
            store.progress.value,
        )
    }

    @Test
    fun `an unknown schema version invalidates the record to not started`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("onboarding_schema_version")] = 99
            preferences[stringPreferencesKey("onboarding_step")] = OnboardingStep.TEST.name
            preferences[booleanPreferencesKey("onboarding_finished")] = true
        }

        val reread = DataStoreOnboardingStore(dataStore, storeScope)

        assertEquals(OnboardingProgress.NOT_STARTED, reread.progress.value)
    }

    @Test
    fun `an unknown step name falls back to welcome and keeps the finished flag`() = runTest {
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("onboarding_schema_version")] = 1
            preferences[stringPreferencesKey("onboarding_step")] = "REMOVED_STEP"
            preferences[booleanPreferencesKey("onboarding_finished")] = true
        }

        val reread = DataStoreOnboardingStore(dataStore, storeScope)

        assertEquals(
            OnboardingProgress(step = OnboardingStep.WELCOME, finished = true),
            reread.progress.value,
        )
    }
}
