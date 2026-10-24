package com.shippopotamus.bloviate.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [CustomStyleSyncGateway] over the `list/save/deleteCustomStyles` HTTPS callables (BLO-25 S4, server BLO-26). */
@Singleton
class FirebaseCustomStyleSyncGateway @Inject constructor() : CustomStyleSyncGateway {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    override suspend fun callListCustomStyles(): Any? =
        call(FUNCTION_LIST, emptyMap<String, Any>())

    override suspend fun callSaveCustomStyle(
        definition: Map<String, Any?>,
        expectedRevision: Long?,
    ): Any? =
        call(
            FUNCTION_SAVE,
            buildMap {
                put("definition", definition)
                if (expectedRevision != null) put("expectedRevision", expectedRevision)
            },
        )

    override suspend fun callDeleteCustomStyle(id: String): Any? =
        call(FUNCTION_DELETE, mapOf("id" to id))

    /**
     * One callable round-trip, mirroring [FirebaseStyleFunctionGateway]:
     * transport trouble throws [StyleFunctionException] carrying the
     * callable's error code. Unlike the style-text call, a missing or
     * oddly shaped payload does NOT throw here — the client's defensive
     * parsing maps it onto `Unavailable`, and fakes script it directly.
     */
    private suspend fun call(function: String, data: Map<String, Any?>): Any? =
        try {
            suspendCancellableCoroutine { continuation ->
                functions.getHttpsCallable(function)
                    .call(data)
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

    /** Transport trouble onto [StyleFunctionException]; the callable's error code rides along. */
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
        const val FUNCTION_LIST: String = "listCustomStyles"
        const val FUNCTION_SAVE: String = "saveCustomStyle"
        const val FUNCTION_DELETE: String = "deleteCustomStyle"
    }
}
