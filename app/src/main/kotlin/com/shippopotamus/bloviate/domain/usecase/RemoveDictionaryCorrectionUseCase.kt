package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.domain.port.DictionaryPort
import javax.inject.Inject

/** Removes the correction for a "heard as" phrase from the personal dictionary; reports the edit outcome. */
class RemoveDictionaryCorrectionUseCase @Inject constructor(
    private val dictionary: DictionaryPort,
) {

    suspend operator fun invoke(heardAs: String): DictionaryEditResult =
        dictionary.removeCorrection(heardAs)
}
