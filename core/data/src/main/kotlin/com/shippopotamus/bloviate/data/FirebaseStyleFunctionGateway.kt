package com.shippopotamus.bloviate.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [StyleFunctionGateway] over the `styleText` HTTPS callable (ENC-151). */
@Singleton
class FirebaseStyleFunctionGateway @Inject constructor() : StyleFunctionGateway {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    override suspend fun callStyleText(styleId: String, transcript: String): String {
        val data = try {
            suspendCancellableCoroutine<Any?> { continuation ->
                functions.getHttpsCallable(FUNCTION_NAME)
                    .call(mapOf("styleId" to styleId, "transcript" to transcript))
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
        } catch (exception: FirebaseFunctionsException) {
            throw StyleFunctionException(
                code = exception.code.name.lowercase().replace('_', '-'),
                message = exception.message,
                cause = exception,
            )
        } catch (exception: StyleFunctionException) {
            throw exception
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw StyleFunctionException(code = "transport-error", message = exception.message, cause = exception)
        }
        val styledText = (data as? Map<*, *>)?.get("styledText") as? String
        if (styledText.isNullOrBlank()) throw StyleFunctionException(code = "bad-response")
        return styledText
    }

    private companion object {
        const val FUNCTION_NAME: String = "styleText"
    }
}
