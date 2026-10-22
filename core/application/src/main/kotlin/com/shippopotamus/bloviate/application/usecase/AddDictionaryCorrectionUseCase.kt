package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.domain.model.DictionaryEditResult
import com.shippopotamus.bloviate.application.port.DictionaryPort
import javax.inject.Inject

/** Adds a "heard as → should be" correction to the personal dictionary; reports the edit outcome. */
class AddDictionaryCorrectionUseCase @Inject constructor(
    private val dictionary: DictionaryPort,
) {

    suspend operator fun invoke(heardAs: String, replacement: String): DictionaryEditResult =
        dictionary.addCorrection(heardAs, replacement)
}
