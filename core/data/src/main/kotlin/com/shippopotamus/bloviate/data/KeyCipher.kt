package com.shippopotamus.bloviate.data

/**
 * One AES/GCM ciphertext: the IV plus the encrypted bytes, both
 * Base64-encoded for DataStore storage. The two halves travel as one
 * versioned string so a partial write can never strand an IV without its
 * ciphertext.
 */
data class KeyCipherText(
    val ivBase64: String,
    val cipherBase64: String,
) {

    fun serialize(): String = ivBase64 + SEPARATOR + cipherBase64

    companion object {

        private const val SEPARATOR: String = "."

        fun parse(serialized: String): KeyCipherText? {
            val halves = serialized.split(SEPARATOR)
            if (halves.size != EXPECTED_HALVES) return null
            val (iv, cipher) = halves
            if (iv.isBlank() || cipher.isBlank()) return null
            return KeyCipherText(ivBase64 = iv, cipherBase64 = cipher)
        }

        private const val EXPECTED_HALVES: Int = 2
    }
}

/**
 * Encrypts the OpenRouter key for rest and decrypts it for use (ENC-73).
 * A small interface so unit tests run against a fake cipher while
 * production goes through the Android Keystore — no key material, real or
 * fake, is ever logged.
 */
interface KeyCipher {

    fun encrypt(plain: String): KeyCipherText

    fun decrypt(payload: KeyCipherText): String
}
