package com.duarf.data.repo

import com.duarf.data.crypto.CryptoEngine
import com.duarf.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

/** Someone the user trusts, offered as a "Call" button on warnings. Opens the dialer only. */
data class FamilyContact(val name: String, val number: String)

/**
 * Stores one optional family contact, encrypted with the same Keystore AES-GCM key as alerts,
 * so no phone number sits in clear on disk (§12). "Delete all data" removes it: the
 * preferences are cleared and the key is destroyed.
 */
class FamilyContactRepository(
    private val preferences: UserPreferencesRepository,
    private val crypto: CryptoEngine
) {
    val contact: Flow<FamilyContact?> = preferences.userPreferencesFlow
        .map { decode(it.familyContactCipher) }
        .distinctUntilChanged()

    suspend fun current(): FamilyContact? = contact.first()

    /** Saves the contact; returns false if the number is not a plausible phone number. */
    suspend fun save(name: String, number: String): Boolean {
        val cleanNumber = normalizeNumber(number) ?: return false
        val cleanName = name.trim().take(MAX_NAME_LENGTH)
        val cipher = crypto.encrypt(cleanName + SEPARATOR + cleanNumber)
        preferences.updateFamilyContactCipher(Base64.getEncoder().encodeToString(cipher))
        return true
    }

    suspend fun clear() {
        preferences.updateFamilyContactCipher(null)
    }

    private fun decode(cipher: String?): FamilyContact? {
        if (cipher == null) return null
        val plain = try {
            crypto.decrypt(Base64.getDecoder().decode(cipher))
        } catch (_: Exception) {
            return null
        }
        val parts = plain.split(SEPARATOR, limit = 2)
        return if (parts.size == 2) FamilyContact(parts[0], parts[1]) else null
    }

    companion object {
        private const val SEPARATOR = "\u0000"
        private const val MAX_NAME_LENGTH = 40

        /** Keeps digits and a leading '+'; accepts 10 to 15 digits. */
        fun normalizeNumber(raw: String): String? {
            val trimmed = raw.trim()
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length !in 10..15) return null
            return if (trimmed.startsWith("+")) "+$digits" else digits
        }
    }
}
