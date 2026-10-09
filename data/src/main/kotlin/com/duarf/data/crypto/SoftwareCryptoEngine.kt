// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard JCE AES-256-GCM and HMAC-SHA256 engine used for testing and headless JVM environments.
 * Strictly adheres to the same on-device binary format: 12-byte IV + AES-GCM ciphertext + tag.
 */
class SoftwareCryptoEngine(
    private val secureRandom: SecureRandom = SecureRandom()
) : CryptoEngine {

    private var aesKey: SecretKey = generateAesKey()
    private var hmacKey: SecretKey = generateHmacKey()

    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, secureRandom)
        return keyGen.generateKey()
    }

    private fun generateHmacKey(): SecretKey {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    override fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
        buffer.put(iv)
        buffer.put(cipherBytes)
        return buffer.array()
    }

    override fun decrypt(encryptedBytes: ByteArray): String {
        if (encryptedBytes.size < 12) return ""
        val iv = ByteArray(12)
        System.arraycopy(encryptedBytes, 0, iv, 0, 12)

        val cipherBytes = ByteArray(encryptedBytes.size - 12)
        System.arraycopy(encryptedBytes, 12, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    override fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    override fun deleteAllKeys() {
        aesKey = generateAesKey()
        hmacKey = generateHmacKey()
    }
}
