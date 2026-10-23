package com.shippopotamus.bloviate.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** [AuthSessionPort] backed by Firebase anonymous auth (ENC-151). Never throws. */
@Singleton
class FirebaseAuthSession @Inject constructor() : AuthSessionPort {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun ensureSignedIn(): String? {
        currentUid()?.let { return it }
        return try {
            suspendCancellableCoroutine { continuation ->
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    val uid = if (task.isSuccessful) task.result?.user?.uid else null
                    continuation.resume(uid)
                }
            }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.w(TAG, "anonymous sign-in failed", exception)
            null
        }
    }

    private companion object {
        const val TAG: String = "FirebaseAuth"
    }
}
