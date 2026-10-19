package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.port.DictionaryPort
import javax.inject.Inject

/** Removes a word from the personal dictionary by name; reports the edit outcome. */
class RemoveDictionaryWordUseCase @Inject constructor(
    private val dictionary: DictionaryPort,
) {

    suspend operator fun invoke(word: String): DictionaryEditResult = dictionary.removeWord(word)
}
