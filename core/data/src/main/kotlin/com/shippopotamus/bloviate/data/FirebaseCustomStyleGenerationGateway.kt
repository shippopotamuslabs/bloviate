package com.shippopotamus.bloviate.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [CustomStyleGenerationGateway] over the `generateCustomStyle` HTTPS callable (BLO-159 S6, BLO-160 S7). */
@Singleton
class FirebaseCustomStyleGenerationGateway @Inject constructor() : CustomStyleGenerationGateway {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    override suspend fun callGenerateCustomStyle(description: String): Any? =
        try {
            suspendCancellableCoroutine { continuation ->
                functions.getHttpsCallable(FUNCTION_GENERATE)
                    .withTimeout(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .call(mapOf(DATA_DESCRIPTION to description))
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw exception.toStyleFunctionException()
        }

    /**
     * Transport trouble onto [StyleFunctionException]: the callable's
     * error code and the server's reason message ride along — the client
     * needs the message to tell a refused description apart from other
     * `invalid-argument` failures.
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
        const val FUNCTION_GENERATE: String = "generateCustomStyle"
        const val DATA_DESCRIPTION: String = "description"

        /**
         * The server runs with `timeoutSeconds: 120` (BLO-159 S6): the
         * client must outlast the whole server window, or a slow-but-alive
         * generation reads as a client timeout first.
         */
        const val GENERATION_TIMEOUT_SECONDS: Long = 150L
    }
}
