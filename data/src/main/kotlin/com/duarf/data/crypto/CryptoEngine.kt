package com.duarf.data.crypto

/**
 * Common cryptographic interface for DUARF on-device storage encryption (§12).
 * Guarantees AES-256-GCM authenticated encryption with 12-byte IV, and HMAC-SHA256
 * for hashing conversation identifiers.
 */
interface CryptoEngine {
    fun encrypt(plaintext: String): ByteArray
    fun decrypt(encryptedBytes: ByteArray): String
    fun computeHmac(data: String): String
    fun deleteAllKeys()
}
