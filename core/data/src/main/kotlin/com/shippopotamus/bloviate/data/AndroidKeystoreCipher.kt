package com.shippopotamus.bloviate.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * [KeyCipher] backed by an AES/GCM key in the Android Keystore (ENC-73).
 * The key never leaves the Keystore; only ciphertext + IV reach DataStore.
 * Framework APIs only — no new dependency for one encrypt/decrypt pair.
 */
class AndroidKeystoreCipher @Inject constructor() : KeyCipher {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun encrypt(plain: String): KeyCipherText {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return KeyCipherText(
            ivBase64 = encode(cipher.iv),
            cipherBase64 = encode(ciphertext),
        )
    }

    override fun decrypt(payload: KeyCipherText): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, decode(payload.ivBase64)))
        }
        val plain = cipher.doFinal(decode(payload.cipherBase64))
        return String(plain, Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { entry ->
            return entry.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(base64: String): ByteArray =
        Base64.decode(base64, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        const val KEY_ALIAS: String = "bloviate_openrouter_key"
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS: Int = 128
    }
}
