package com.shippopotamus.bloviate.domain.usecase

import com.shippopotamus.bloviate.domain.model.PersonalDictionary
import com.shippopotamus.bloviate.domain.port.DictionaryPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams the user's personal dictionary (words + corrections). */
class ObserveDictionaryUseCase @Inject constructor(
    private val dictionary: DictionaryPort,
) {

    operator fun invoke(): StateFlow<PersonalDictionary> = dictionary.dictionary
}
