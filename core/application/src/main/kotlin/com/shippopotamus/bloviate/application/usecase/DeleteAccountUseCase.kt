package com.shippopotamus.bloviate.application.usecase

import com.shippopotamus.bloviate.application.port.AccountDeletionOutcome
import com.shippopotamus.bloviate.application.port.AccountDeletionPort
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.CustomStyleStorePort
import com.shippopotamus.bloviate.application.port.LastStyledResultPort
import com.shippopotamus.bloviate.application.port.StylePreferencePort
import com.shippopotamus.bloviate.domain.model.StyleCatalog
import javax.inject.Inject

/**
 * Deletes the signed-in account (BLO-178). The server call runs FIRST
 * and alone decides the outcome: only on
 * [AccountDeletionOutcome.Deleted] does any local cleanup happen, so a
 * failed or refused call leaves the account — and its local cache —
 * fully intact for a retry.
 *
 * Local cleanup on [AccountDeletionOutcome.Deleted], in order: wipe
 * the account-owned custom-style cache ([CustomStyleStorePort.clearLocalCache]),
 * drop the session's last styled result, reset a custom-style
 * selection to the catalog default (the deleted style can never
 * resolve again), then sign out locally. The server already deleted
 * the Firebase Auth user, so the local sign-out is Credential Manager
 * cleanup only — its failure is swallowed and can never turn a
 * completed deletion back into a failure.
 */
class DeleteAccountUseCase @Inject constructor(
    private val deletionPort: AccountDeletionPort,
    private val authSession: AuthSessionPort,
    private val customStyleStore: CustomStyleStorePort,
    private val stylePreference: StylePreferencePort,
    private val lastStyledResult: LastStyledResultPort,
) {

    suspend operator fun invoke(): AccountDeletionOutcome {
        val outcome = deletionPort.deleteAccount()
        if (outcome != AccountDeletionOutcome.Deleted) return outcome
        customStyleStore.clearLocalCache()
        lastStyledResult.clear()
        if (stylePreference.selectedStyleId.value.startsWith(CUSTOM_STYLE_ID_PREFIX)) {
            stylePreference.selectStyle(StyleCatalog.DEFAULT.id)
        }
        runCatching { authSession.signOut() }
        return AccountDeletionOutcome.Deleted
    }

    private companion object {
        const val CUSTOM_STYLE_ID_PREFIX: String = "custom-"
    }
}
