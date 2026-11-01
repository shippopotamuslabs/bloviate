package com.shippopotamus.bloviate.application.port

/**
 * The outcome of one account-deletion call (BLO-178). Every outcome is
 * terminal, never a throw: the caller decides what the user sees, and
 * only [Deleted] may clear local account data.
 */
enum class AccountDeletionOutcome {
    /** The server deleted the account and its records; the Auth user is gone. */
    Deleted,

    /** The server refused: the session's sign-in is too old. Re-authenticate and retry. */
    RequiresRecentLogin,

    /** No signed-in user; the caller never reached the network. */
    SignedOut,

    /** Transport trouble or another server failure; the account was not deleted. Retryable. */
    Unavailable,
}

/**
 * The server side of account deletion (BLO-178): the `deleteAccount`
 * callable, which derives the uid from the caller's verified token —
 * the client never sends a uid. Implementations map every failure onto
 * [AccountDeletionOutcome], never a throw (except coroutine
 * cancellation, which is rethrown).
 */
interface AccountDeletionPort {

    /** Deletes the signed-in account server-side, Auth user included. */
    suspend fun deleteAccount(): AccountDeletionOutcome
}
