package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.LastStyledResult
import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Streams this session's last styled dictation, for the Setup report card (BLO-148). */
class ObserveLastStyledResultUseCase @Inject constructor(
    private val lastStyledResult: LastStyledResultPort,
) {

    operator fun invoke(): StateFlow<LastStyledResult?> = lastStyledResult.lastResult
}
