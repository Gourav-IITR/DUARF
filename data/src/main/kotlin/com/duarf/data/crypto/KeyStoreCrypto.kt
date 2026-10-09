// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreCrypto : CryptoEngine {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val secureRandom = SecureRandom()

    init {
        ensureAesKey()
        ensureHmacKey()
    }

    private fun ensureAesKey() {
        if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                AES_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Required so listener can write while locked (§12)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun ensureHmacKey() {
        if (!keyStore.containsAlias(HMAC_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                HMAC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    override fun encrypt(plaintext: String): ByteArray {
        val secretKey = keyStore.getKey(AES_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12-byte IV

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend 12-byte IV to ciphertext
        val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
        buffer.put(iv)
        buffer.put(cipherBytes)
        return buffer.array()
    }

    override fun decrypt(encryptedBytes: ByteArray): String {
        if (encryptedBytes.size < 12) return ""
        val secretKey = keyStore.getKey(AES_KEY_ALIAS, null) as SecretKey

        val iv = ByteArray(12)
        System.arraycopy(encryptedBytes, 0, iv, 0, 12)

        val cipherBytes = ByteArray(encryptedBytes.size - 12)
        System.arraycopy(encryptedBytes, 12, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    override fun computeHmac(data: String): String {
        val secretKey = keyStore.getKey(HMAC_KEY_ALIAS, null) as SecretKey
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    override fun deleteAllKeys() {
        if (keyStore.containsAlias(AES_KEY_ALIAS)) {
            keyStore.deleteEntry(AES_KEY_ALIAS)
        }
        if (keyStore.containsAlias(HMAC_KEY_ALIAS)) {
            keyStore.deleteEntry(HMAC_KEY_ALIAS)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_ALIAS = "duarf_aes_gcm_key"
        private const val HMAC_KEY_ALIAS = "duarf_hmac_key"
    }
}
