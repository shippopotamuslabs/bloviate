package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.AccountDeletionPort

/** Hand-written fake of [AccountDeletionPort]; script the outcome, count the calls. */
class FakeAccountDeletionPort(
    var outcome: AccountDeletionOutcome = AccountDeletionOutcome.Deleted,
) : AccountDeletionPort {

    var callCount: Int = 0
        private set

    override suspend fun deleteAccount(): AccountDeletionOutcome {
        callCount++
        return outcome
    }
}
