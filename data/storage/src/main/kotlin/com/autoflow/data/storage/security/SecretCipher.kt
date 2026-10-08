package com.autoflow.data.storage.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts sensitive values (secret variables such as API tokens) before they are written to disk. */
interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

/**
 * AES-256/GCM with a non-exportable key held by the Android Keystore.
 * Output format: `v1:<base64 iv>:<base64 ciphertext>`.
 */
class KeystoreSecretCipher : SecretCipher {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(VERSION, cipher.iv.toBase64(), encrypted.toBase64()).joinToString(SEPARATOR)
    }

    override fun decrypt(cipherText: String): String {
        val parts = cipherText.split(SEPARATOR)
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported secret format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, parts[1].fromBase64()))
        return String(cipher.doFinal(parts[2].fromBase64()), Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "autoflow_secret_variables"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val VERSION = "v1"
        const val SEPARATOR = ":"
    }
}
