package com.shippopotamus.bloviate.presentation

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import java.io.IOException

/**
 * Maps Google / Credential Manager failures to short, user-facing copy
 * (ENC-197, adapted from Stratum's `GoogleSignInErrorMapper`). Never
 * returns exception class names, stack-ish text, or developer config
 * jargon — technical detail stays in logcat (`GoogleAuth` tag).
 */
fun Throwable.toGoogleSignInMessage(): String {
    val chain = generateSequence<Throwable>(this) { it.cause }.toList()
    // A stale device session surfaces as a cancellation whose message (or a
    // cause's message) reports reauth failure — that needs visible guidance,
    // not the silent user-dismissed path.
    if (chain.any { it is GetCredentialCancellationException } && chain.indicatesReauthFailure()) {
        return MSG_REAUTH
    }
    // Prefer typed Credential Manager / IO failures over free-form messages on wrappers.
    chain.forEach { typedMessage(it)?.let { message -> return message } }
    chain.forEach { heuristicMessage(it)?.let { message -> return message } }
    return MSG_GENERIC
}

/**
 * A user-dismissed flow is silent (ENC-197): cancellation never becomes
 * an error line on the sign-in screen. A cancellation caused by a stale
 * device session ("reauth" in the message or cause chain) is not a
 * dismissal — it must surface [MSG_REAUTH] instead.
 */
fun Throwable.isSignInDismissal(): Boolean =
    this is GetCredentialCancellationException &&
        !generateSequence<Throwable>(this) { it.cause }.toList().indicatesReauthFailure()

private fun List<Throwable>.indicatesReauthFailure(): Boolean =
    any { it.message?.contains("reauth", ignoreCase = true) == true }

private fun typedMessage(error: Throwable): String? =
    when (error) {
        is GetCredentialCancellationException -> MSG_CANCELLED
        is NoCredentialException -> MSG_NO_ACCOUNT
        is GetCredentialInterruptedException -> MSG_INTERRUPTED
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException,
        -> MSG_UNAVAILABLE
        is IOException -> MSG_NETWORK
        else -> null
    }

private fun heuristicMessage(error: Throwable): String? {
    val message = error.message.orEmpty()
    if (message.isBlank()) return null

    val lower = message.lowercase()
    return when {
        lower.contains("cancel") -> MSG_CANCELLED
        lower.contains("default_web_client_id") ||
            lower.contains("not configured") ||
            lower.contains("isn't configured") ||
            (lower.contains("oauth") && lower.contains("client")) ||
            lower.contains("developer_error") ||
            lower.contains("sign-in configuration") ||
            lower.contains("signin configuration")
        -> MSG_CONFIG
        lower.contains("network") ||
            lower.contains("unable to resolve host") ||
            lower.contains("timeout") ||
            lower.contains("connection")
        -> MSG_NETWORK
        lower.contains("no credentials") ||
            lower.contains("no account") ||
            lower.contains("cannot find a matching credential")
        -> MSG_NO_ACCOUNT
        looksTechnical(message) -> null
        // Short, already human copy from our own code / known libs.
        message.length <= MAX_FRIENDLY_LENGTH -> message
        else -> null
    }
}

private fun looksTechnical(message: String): Boolean {
    val lower = message.lowercase()
    return lower.contains("exception") ||
        lower.contains("error:") ||
        lower.contains("illegal") ||
        lower.contains("nullpointer") ||
        lower.contains(" at ") ||
        lower.contains("com.") ||
        lower.contains("android.") ||
        lower.contains("kotlin.") ||
        lower.contains("status code") ||
        lower.contains("api_not_available") ||
        message.lines().size > 1
}

// User-facing copy (English).
internal const val MSG_GENERIC = "Couldn't sign in with Google. Try again."
internal const val MSG_CANCELLED = "Sign-in cancelled."
internal const val MSG_NO_ACCOUNT =
    "No Google account found on this device. Add one in Settings and try again."
internal const val MSG_INTERRUPTED = "Sign-in was interrupted. Try again."
internal const val MSG_UNAVAILABLE = "Google Sign-In isn't available on this device."
internal const val MSG_NETWORK = "Couldn't reach Google. Check your connection and try again."
internal const val MSG_CONFIG =
    "Sign-in isn't available in this build. Update the app or try again later."
internal const val MSG_REAUTH =
    "Your Google account needs a fresh sign-in on this device. Open Settings > Accounts, sign in again, then try again."

private const val MAX_FRIENDLY_LENGTH = 120
