package com.shippopotamus.bloviate.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [AccountDeletionGateway] over the `deleteAccount` HTTPS callable (BLO-178). */
@Singleton
class FirebaseAccountDeletionGateway @Inject constructor() : AccountDeletionGateway {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    override suspend fun callDeleteAccount(): Any? =
        try {
            suspendCancellableCoroutine { continuation ->
                functions.getHttpsCallable(FUNCTION_NAME)
                    .call(emptyMap<String, Any>())
                    .addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful) {
                            continuation.resume(task.result?.data)
                        } else {
                            continuation.resumeWithException(
                                task.exception ?: StyleFunctionException(code = "transport-error"),
                            )
                        }
                    }
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw exception.toStyleFunctionException()
        }

    /**
     * Transport trouble onto [StyleFunctionException]; the callable's
     * error code AND message ride along — the deletion client reads the
     * message for the server's `requires-recent-login` marker.
     */
    private fun Exception.toStyleFunctionException(): StyleFunctionException =
        when (this) {
            is StyleFunctionException -> this
            is FirebaseFunctionsException ->
                StyleFunctionException(
                    code = code.name.lowercase().replace('_', '-'),
                    message = message,
                    cause = this,
                )
            else -> StyleFunctionException(code = "transport-error", message = message, cause = this)
        }

    private companion object {
        const val FUNCTION_NAME: String = "deleteAccount"
    }
}
