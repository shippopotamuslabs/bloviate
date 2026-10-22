package com.shippopotamus.bloviate.application.port

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's persisted personal dictionary (ENC-74). [dictionary] is the
 * live snapshot the cleanup pipeline reads at send time, so edits made in
 * the Dictionary tab take effect on the very next dictation — nothing in
 * the pipeline captures the dictionary at startup.
 *
 * All edits enforce the rules on [PersonalDictionary] (normalization,
 * blank/duplicate/self-correction rejection) and report the outcome as a
 * [DictionaryEditResult]; the dictionary only changes on
 * [DictionaryEditResult.APPLIED].
 */
interface DictionaryPort {

    /** The current dictionary; the seeded starter set until the user edits it. */
    val dictionary: StateFlow<PersonalDictionary>

    /** Adds a canonical-casing word; see [PersonalDictionary.withWordAdded]. */
    suspend fun addWord(word: String): DictionaryEditResult

    /** Removes a word by name (case-insensitive); see [PersonalDictionary.withWordRemoved]. */
    suspend fun removeWord(word: String): DictionaryEditResult

    /** Adds a "heard as → should be" correction; see [PersonalDictionary.withCorrectionAdded]. */
    suspend fun addCorrection(heardAs: String, replacement: String): DictionaryEditResult

    /** Removes the correction for [heardAs] (case-insensitive); see [PersonalDictionary.withCorrectionRemoved]. */
    suspend fun removeCorrection(heardAs: String): DictionaryEditResult
}
