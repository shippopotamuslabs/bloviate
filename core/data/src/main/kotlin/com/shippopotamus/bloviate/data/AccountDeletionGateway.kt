package com.shippopotamus.bloviate.data

/**
 * The raw account-deletion callable surface (BLO-178): the narrow,
 * fake-testable seam under [FirebaseAccountDeletionClient], mirroring
 * the [CustomStyleSyncGateway] split. The call returns the callable's
 * raw `data` payload and throws [StyleFunctionException] carrying the
 * callable's error code (and message) on transport or server trouble.
 */
interface AccountDeletionGateway {

    /** Calls `deleteAccount` with `{}` — the server derives the uid from the caller's token. */
    suspend fun callDeleteAccount(): Any?
}
