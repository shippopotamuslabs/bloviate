package com.shippopotamus.bloviate.data

import android.util.Log
import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.AccountDeletionPort
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [AccountDeletionPort] via the `deleteAccount` callable (BLO-178),
 * mirroring [FirebaseCustomStyleSyncClient]: a signed-out user gets
 * [AccountDeletionOutcome.SignedOut] without a network call, and every
 * failure becomes a typed outcome, never a throw (except coroutine
 * cancellation). Logs carry outcome codes only.
 *
 * Recent-login mapping: the server throws `failed-precondition` with
 * the message `requires-recent-login` when the token's auth time is
 * too old — and that is the ONLY `failed-precondition` this callable
 * throws, so any `failed-precondition` from it maps to
 * [AccountDeletionOutcome.RequiresRecentLogin] even if a transport
 * layer drops the message on the way.
 */
class FirebaseAccountDeletionClient @Inject constructor(
    private val gateway: AccountDeletionGateway,
    private val auth: AuthSessionPort,
) : AccountDeletionPort {

    override suspend fun deleteAccount(): AccountDeletionOutcome {
        if (auth.ensureSignedIn() == null) {
            Log.d(TAG, "account-deletion outcome=signed-out")
            return AccountDeletionOutcome.SignedOut
        }
        return try {
            gateway.callDeleteAccount()
            Log.d(TAG, "account-deletion outcome=deleted")
            AccountDeletionOutcome.Deleted
        } catch (exception: StyleFunctionException) {
            val outcome = if (exception.code == CODE_FAILED_PRECONDITION) {
                AccountDeletionOutcome.RequiresRecentLogin
            } else {
                AccountDeletionOutcome.Unavailable
            }
            Log.d(TAG, "account-deletion outcome=$outcome code=${exception.code}")
            outcome
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.d(TAG, "account-deletion outcome=unavailable", exception)
            AccountDeletionOutcome.Unavailable
        }
    }

    private companion object {
        const val TAG: String = "AccountDeletion"
        const val CODE_FAILED_PRECONDITION: String = "failed-precondition"
    }
}
