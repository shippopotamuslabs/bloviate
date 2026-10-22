package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.application.port.DictionaryPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Hand-written fake of [DictionaryPort]. Enforces the real edit rules by
 * delegating to [PersonalDictionary]'s own pure edit functions — the same
 * ones the DataStore store uses — so outcomes here cannot drift from
 * production. Starts from [PersonalDictionary.SEED], mirroring the real
 * store's first-run state; pass another dictionary to start elsewhere.
 */
class FakeDictionaryPort(
    initial: PersonalDictionary = PersonalDictionary.SEED,
) : DictionaryPort {

    private val mutableDictionary = MutableStateFlow(initial)

    override val dictionary: StateFlow<PersonalDictionary> = mutableDictionary.asStateFlow()

    override suspend fun addWord(word: String): DictionaryEditResult =
        applyEdit { current -> current.withWordAdded(word) }

    override suspend fun removeWord(word: String): DictionaryEditResult =
        applyEdit { current -> current.withWordRemoved(word) }

    override suspend fun addCorrection(heardAs: String, replacement: String): DictionaryEditResult =
        applyEdit { current -> current.withCorrectionAdded(heardAs, replacement) }

    override suspend fun removeCorrection(heardAs: String): DictionaryEditResult =
        applyEdit { current -> current.withCorrectionRemoved(heardAs) }

    /** Replaces the whole dictionary without edit rules — for arranging pipeline/UI scenarios directly. */
    fun setDictionary(dictionary: PersonalDictionary) {
        mutableDictionary.value = dictionary
    }

    private fun applyEdit(
        edit: (PersonalDictionary) -> Pair<DictionaryEditResult, PersonalDictionary>,
    ): DictionaryEditResult {
        var result = DictionaryEditResult.NOT_FOUND
        mutableDictionary.update { current ->
            val (outcome, updated) = edit(current)
            result = outcome
            updated
        }
        return result
    }
}
