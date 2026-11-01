package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import javax.inject.Inject

/**
 * Drops this session's last styled result (BLO-148): sign-out clears
 * it so one account's dictation can never be reported — or even shown
 * — under another. (Account deletion clears it inside
 * `DeleteAccountUseCase`.)
 */
class ClearLastStyledResultUseCase @Inject constructor(
    private val lastStyledResult: LastStyledResultPort,
) {

    operator fun invoke() {
        lastStyledResult.clear()
    }
}
