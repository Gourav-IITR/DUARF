package com.duarf.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    private lateinit var crypto: SoftwareCryptoEngine

    @Before
    fun setUp() {
        crypto = SoftwareCryptoEngine()
    }

    @Test
    fun `encrypts and decrypts round trip successfully`() {
        val message = "Urgent: Your SBI account is blocked! Update KYC at https://sbi-kyc.fake"
        val encrypted = crypto.encrypt(message)

        assertThat(encrypted.size).isGreaterThan(12) // 12-byte IV + ciphertext + tag
        val decrypted = crypto.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(message)
    }

    @Test
    fun `encrypted bytes do not contain plaintext canary`() {
        val canary = "CANARY_SECRET_DATA_XYZ_987654"
        val encrypted = crypto.encrypt(canary)

        val canaryBytes = canary.toByteArray(Charsets.UTF_8)
        val encryptedStr = String(encrypted, Charsets.ISO_8859_1)
        val canaryStr = String(canaryBytes, Charsets.ISO_8859_1)

        assertThat(encryptedStr.contains(canaryStr)).isFalse()
    }

    @Test
    fun `encryption produces distinct IV for each invocation`() {
        val message = "Constant text"
        val enc1 = crypto.encrypt(message)
        val enc2 = crypto.encrypt(message)

        val iv1 = enc1.copyOfRange(0, 12)
        val iv2 = enc2.copyOfRange(0, 12)

        assertThat(iv1).isNotEqualTo(iv2)
        assertThat(enc1).isNotEqualTo(enc2)
    }

    @Test
    fun `tampered ciphertext throws AEADBadTagException`() {
        val message = "Authentic banking alert"
        val encrypted = crypto.encrypt(message)

        // Corrupt one byte of ciphertext
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt(encrypted)
        }
    }

    @Test
    fun `HMAC produces 64-char hex string with high entropy`() {
        val chat1 = "chat_user_9876543210"
        val chat2 = "chat_user_9876543211"

        val hmac1 = crypto.computeHmac(chat1)
        val hmac2 = crypto.computeHmac(chat2)

        assertThat(hmac1).hasLength(64)
        assertThat(hmac2).hasLength(64)
        assertThat(hmac1).isNotEqualTo(hmac2)

        // Deterministic for same input with same key
        assertThat(crypto.computeHmac(chat1)).isEqualTo(hmac1)
    }

    @Test
    fun `deleteAllKeys resets key material and prevents past decryption`() {
        val secret = "Secret message before wipe"
        val encrypted = crypto.encrypt(secret)

        crypto.deleteAllKeys()

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt(encrypted)
        }
    }
}
