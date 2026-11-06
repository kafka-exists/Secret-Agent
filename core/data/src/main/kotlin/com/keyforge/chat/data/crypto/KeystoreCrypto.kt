package com.keyforge.chat.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM encryption backed by the Android Keystore. The key never leaves the
 * Keystore (hardware-backed where available); only ciphertext travels through
 * Room and DataStore. This replaces the deprecated security-crypto library and
 * satisfies the "keys encrypted at rest, never plaintext" requirement.
 *
 * Storage format: base64( iv(12) || ciphertext || tag(16) ). GCM tag is appended
 * to the ciphertext by the JCE provider, so we prepend only the IV.
 */
class KeystoreCrypto(
    private val alias: String = DEFAULT_ALIAS,
) {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(alias, null)?.let { return it as SecretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ct
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(ciphertextB64: String): String {
        val combined = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        require(combined.size > IV_SIZE) { "ciphertext too short" }
        val iv = combined.copyOfRange(0, IV_SIZE)
        val ct = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct).toString(Charsets.UTF_8)
    }

    /** Returns a masked view for UI display: first4…last4, never the full key. */
    fun mask(value: String): String = when {
        value.length <= 8 -> "•".repeat(value.length)
        else -> value.take(4) + "…" + value.takeLast(4)
    }

    companion object {
        private const val DEFAULT_ALIAS = "keyforge_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
