package com.shippopotamus.bloviate.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedirectResolverTest {

    @Test
    fun `redirect statuses are recognized`() {
        listOf(301, 302, 303, 307, 308).forEach { statusCode ->
            assertTrue(RedirectResolver.isRedirect(statusCode), "HTTP $statusCode should redirect")
        }
        listOf(200, 204, 400, 403, 404, 500).forEach { statusCode ->
            assertFalse(RedirectResolver.isRedirect(statusCode), "HTTP $statusCode should not redirect")
        }
    }

    @Test
    fun `non-redirect status yields no next url`() {
        assertNull(RedirectResolver.nextUrl(HF_URL, 200, CDN_URL, redirectsFollowed = 0))
    }

    @Test
    fun `absolute location is followed verbatim`() {
        assertEquals(
            CDN_URL,
            RedirectResolver.nextUrl(HF_URL, 302, CDN_URL, redirectsFollowed = 0),
        )
    }

    @Test
    fun `relative location resolves against the current url`() {
        assertEquals(
            "https://huggingface.co/cdn/signed.bin",
            RedirectResolver.nextUrl(HF_URL, 302, "/cdn/signed.bin", redirectsFollowed = 0),
        )
    }

    @Test
    fun `missing or blank location yields no next url`() {
        assertNull(RedirectResolver.nextUrl(HF_URL, 302, null, redirectsFollowed = 0))
        assertNull(RedirectResolver.nextUrl(HF_URL, 302, "  ", redirectsFollowed = 0))
    }

    @Test
    fun `redirect limit stops following`() {
        assertNull(
            RedirectResolver.nextUrl(
                HF_URL,
                302,
                CDN_URL,
                redirectsFollowed = RedirectResolver.MAX_REDIRECTS,
            ),
        )
        assertEquals(
            CDN_URL,
            RedirectResolver.nextUrl(
                HF_URL,
                302,
                CDN_URL,
                redirectsFollowed = RedirectResolver.MAX_REDIRECTS - 1,
            ),
        )
    }

    @Test
    fun `every redirect status follows`() {
        listOf(301, 302, 303, 307, 308).forEach { statusCode ->
            assertEquals(
                CDN_URL,
                RedirectResolver.nextUrl(HF_URL, statusCode, CDN_URL, redirectsFollowed = 0),
                "HTTP $statusCode should follow its Location",
            )
        }
    }

    private companion object {
        const val HF_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
        const val CDN_URL = "https://us.aws.cdn.hf.co/xet-bridge/signed/ggml-base.en.bin"
    }
}
