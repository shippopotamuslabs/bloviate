package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.port.DictionaryPort
import javax.inject.Inject

/** Adds a canonical-casing word to the personal dictionary; reports the edit outcome. */
class AddDictionaryWordUseCase @Inject constructor(
    private val dictionary: DictionaryPort,
) {

    suspend operator fun invoke(word: String): DictionaryEditResult = dictionary.addWord(word)
}
