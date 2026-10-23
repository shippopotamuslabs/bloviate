package com.shippopotamus.bloviate.data

/** Failure of the style Function call, carrying the callable's error [code]. */
class StyleFunctionException(
    val code: String,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Calls the deployed `styleText` callable Function (ENC-148/ENC-151).
 * Returns the styled text; throws [StyleFunctionException] carrying the
 * callable's error code (e.g. `permission-denied`, `unavailable`).
 */
interface StyleFunctionGateway {

    /** Styles [transcript] with [styleId] via the Function. */
    suspend fun callStyleText(styleId: String, transcript: String): String
}
