package com.shippopotamus.bloviate.data

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.shippopotamus.bloviate.application.port.AuthSessionPort
import com.shippopotamus.bloviate.application.port.AuthState
import com.shippopotamus.bloviate.application.port.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [AuthSessionPort] via Credential Manager "Sign in with Google" plus
 * Firebase (ENC-197, adapted from Stratum's
 * `FirebaseGoogleAuthManager`). Anonymous Firebase users count as
 * signed out for the gate; a Google sign-in over one links the
 * credential onto it, falling back to a plain sign-in when the Google
 * account already exists in Firebase.
 *
 * Unlike the ENC-151 session this replaces, nothing here ever creates
 * an anonymous session: [ensureSignedIn] is a read.
 */
@Singleton
class GoogleAuthSession @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val signInHostContext: SignInHostContext,
    @GoogleServerClientId private val googleServerClientId: String,
) : AuthSessionPort {

    private val _authState = MutableStateFlow(toAuthStateOf(auth.currentUser))
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _authState.value = toAuthStateOf(firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(listener)
    }

    override fun currentUid(): String? =
        auth.currentUser?.takeIf { !it.isAnonymous }?.uid

    override suspend fun ensureSignedIn(): String? = currentUid()

    override suspend fun signInWithGoogle(): Result<UserProfile> = runCatching {
        if (googleServerClientId.isBlank()) {
            throw IllegalStateException("Google Sign-In is not configured in this build.")
        }
        val signInWithGoogleOption =
            GetSignInWithGoogleOption.Builder(serverClientId = googleServerClientId).build()
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
        val hostContext = signInHostContext.requireContext()
        val response = credentialManager.getCredential(hostContext, request)
        val idToken =
            when (val credential = response.credential) {
                is GoogleIdTokenCredential -> credential.idToken
                is CustomCredential if credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                    GoogleIdTokenCredential.createFrom(credential.data).idToken

                else -> error("Unexpected credential type: ${response.credential.type}")
            }
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        val authResult =
            if (current != null && current.isAnonymous) {
                try {
                    linkAwait(current, firebaseCredential)
                } catch (collision: FirebaseAuthUserCollisionException) {
                    // The Google account already exists in Firebase
                    // (a previous install, for example): the anonymous
                    // shell has nothing worth keeping, so sign in
                    // directly instead of linking.
                    Log.d(TAG, "anonymous link collided; signing in directly", collision)
                    signInAwait(firebaseCredential)
                }
            } else {
                signInAwait(firebaseCredential)
            }
        val user = authResult.user ?: error("Firebase sign in failed.")
        UserProfile(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString(),
        )
    }.onFailure { failure ->
        Log.w(TAG, "Google sign-in failed", failure)
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        auth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }.onFailure { failure ->
        Log.w(TAG, "Google sign-out failed", failure)
    }

    private suspend fun linkAwait(user: FirebaseUser, credential: AuthCredential): AuthResult =
        suspendCancellableCoroutine { continuation ->
            user.linkWithCredential(credential).addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Account linking failed."),
                    )
                }
            }
        }

    private suspend fun signInAwait(credential: AuthCredential): AuthResult =
        suspendCancellableCoroutine { continuation ->
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firebase sign in failed."),
                    )
                }
            }
        }

    private companion object {
        const val TAG: String = "GoogleAuth"
    }
}

private fun toAuthStateOf(user: FirebaseUser?): AuthState =
    toAuthState(
        uid = user?.uid,
        displayName = user?.displayName,
        email = user?.email,
        photoUrl = user?.photoUrl?.toString(),
        isAnonymous = user?.isAnonymous ?: false,
    )
