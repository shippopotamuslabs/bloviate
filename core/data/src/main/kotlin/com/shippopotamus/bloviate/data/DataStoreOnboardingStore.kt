package com.shippopotamus.bloviate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shippopotamus.bloviate.domain.model.OnboardingProgress
import com.shippopotamus.bloviate.domain.model.OnboardingStep
import com.shippopotamus.bloviate.application.port.OnboardingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OnboardingPort] backed by the shared preferences DataStore.
 *
 * Persisted format (schema v1, see `contracts/DATA_LAYER_RULES.md` §2):
 * a version int, the resume step's enum name, and the finished flag.
 * A missing or unknown version reads as [OnboardingProgress.NOT_STARTED]
 * — an explicit invalidation, never a silent reinterpretation. Within
 * a v1 record each field invalidates independently: an unrecognized
 * step name falls back to the walk's start (a removed step can never
 * wedge the wizard), and a missing finished flag reads as unfinished,
 * the only reading that can re-show the wizard rather than strand a
 * user who never completed it. The next save rewrites the record at v1.
 */
@Singleton
class DataStoreOnboardingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : OnboardingPort {

    override val progress: StateFlow<OnboardingProgress> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toOnboardingProgress() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = OnboardingProgress.NOT_STARTED,
        )

    override suspend fun save(progress: OnboardingProgress) {
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = SCHEMA_VERSION
            preferences[KEY_STEP] = progress.step.name
            preferences[KEY_FINISHED] = progress.finished
        }
    }

    private fun Preferences.toOnboardingProgress(): OnboardingProgress {
        if (this[KEY_SCHEMA_VERSION] != SCHEMA_VERSION) return OnboardingProgress.NOT_STARTED
        return OnboardingProgress(
            step = this[KEY_STEP]?.let { stored ->
                runCatching { OnboardingStep.valueOf(stored) }.getOrNull()
            } ?: OnboardingStep.WELCOME,
            finished = this[KEY_FINISHED] ?: false,
        )
    }

    private companion object {
        const val SCHEMA_VERSION: Int = 1
        val KEY_SCHEMA_VERSION = intPreferencesKey("onboarding_schema_version")
        val KEY_STEP = stringPreferencesKey("onboarding_step")
        val KEY_FINISHED = booleanPreferencesKey("onboarding_finished")
    }
}
