package com.shippopotamus.bloviate.data

import java.net.URL

/**
 * Pure redirect decision for [HttpModelFileDownloader]'s manual redirect
 * loop. Extracted so the policy (which statuses redirect, the hop limit,
 * relative-`Location` resolution) is unit-testable without a live server.
 */
internal object RedirectResolver {

    /** Maximum number of redirects followed for a single download. */
    const val MAX_REDIRECTS: Int = 5

    private val REDIRECT_STATUS_CODES: Set<Int> = setOf(301, 302, 303, 307, 308)

    /** Whether [statusCode] is an HTTP redirect status. */
    fun isRedirect(statusCode: Int): Boolean = statusCode in REDIRECT_STATUS_CODES

    /**
     * The absolute URL to request next, or null when this hop is final:
     * not a redirect status, a missing/blank `Location`, or the redirect
     * limit already reached. A relative [location] resolves against
     * [currentUrl] (the Hugging Face → CDN hop is absolute, but the
     * resolver does not rely on that).
     */
    fun nextUrl(
        currentUrl: String,
        statusCode: Int,
        location: String?,
        redirectsFollowed: Int,
    ): String? {
        if (!isRedirect(statusCode)) return null
        if (location.isNullOrBlank()) return null
        if (redirectsFollowed >= MAX_REDIRECTS) return null
        return URL(URL(currentUrl), location).toString()
    }
}
