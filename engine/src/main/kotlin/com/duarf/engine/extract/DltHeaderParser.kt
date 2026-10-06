package com.duarf.engine.extract

import com.duarf.engine.model.SenderKind

data class ParsedSender(
    val senderKind: SenderKind,
    val dltPrefix: String? = null,
    val dltBrand: String? = null,
    val dltSuffix: String? = null,
    val countryCode: String? = null
)

object DltHeaderParser {

    // TRAI TCCCPR Header format:
    // Typically: 2-letter prefix (operator + circle), hyphen, 3-9 alphanumeric brand/entity, optional hyphen, suffix (P, S, T, G)
    // Examples: AX-HDFCBK-T, VM-SBIBNK-T, BZ-SWIGGY-S, AX-MYNTRA-P, JK-EPFOGV-G, AD-ICICIB
    private val dltHeaderPrefixedPattern = Regex(
        """^([A-Za-z]{2})-([A-Za-z0-9]{3,9})(?:-([PSTGpstg]))?$"""
    )

    // Bare header format (e.g. HDFCBK-T, SBIBNK-T, or 6-char alpha header HDFCBK)
    private val dltHeaderBarePattern = Regex(
        """^([A-Za-z]{3,8})-([PSTGpstg])$"""
    )

    // Indian personal mobile number: optional +91/91/0 followed by 10 digits starting with 6, 7, 8, or 9
    // Handles spaces and hyphens e.g. +91 98765 43210, +91-9876543210, 9876543210
    private val personalMobilePattern = Regex(
        """^(?:\+?91[\s\-]??|0)?([6-9]\d{9})$"""
    )

    // General international 10-digit mobile number
    private val generalMobilePattern = Regex(
        """^(?:\+(\d{1,3})[\s\-]??)?(\d{10})$"""
    )

    // Short code (3 to 6 digits, e.g. 1901, 56767, 144)
    private val shortCodePattern = Regex(
        """^\d{3,6}$"""
    )

    fun parse(senderDisplay: String?): ParsedSender {
        val trimmed = senderDisplay?.trim() ?: return ParsedSender(SenderKind.UNKNOWN)
        if (trimmed.isEmpty()) return ParsedSender(SenderKind.UNKNOWN)

        // 1. Check TRAI DLT Header with operator prefix (most common for Indian commercial SMS)
        val prefixedMatch = dltHeaderPrefixedPattern.matchEntire(trimmed)
        if (prefixedMatch != null) {
            val prefix = prefixedMatch.groupValues[1].uppercase()
            val brand = prefixedMatch.groupValues[2].uppercase()
            val suffix = prefixedMatch.groupValues[3].takeIf { it.isNotEmpty() }?.uppercase()
            return ParsedSender(
                senderKind = SenderKind.DLT_HEADER,
                dltPrefix = prefix,
                dltBrand = brand,
                dltSuffix = suffix
            )
        }

        // 2. Check DLT Header without operator prefix (e.g. HDFCBK-T)
        val bareMatch = dltHeaderBarePattern.matchEntire(trimmed)
        if (bareMatch != null) {
            val brand = bareMatch.groupValues[1].uppercase()
            val suffix = bareMatch.groupValues[2].uppercase()
            return ParsedSender(
                senderKind = SenderKind.DLT_HEADER,
                dltPrefix = null,
                dltBrand = brand,
                dltSuffix = suffix
            )
        }

        // Clean digits for phone number checks (strip spaces, hyphens, parentheses)
        val cleaned = trimmed.replace(Regex("""[\s\-\(\)]"""), "")

        // 3. Check Indian Personal Mobile Number
        val mobileMatch = personalMobilePattern.matchEntire(cleaned)
        if (mobileMatch != null) {
            return ParsedSender(
                senderKind = SenderKind.PERSONAL_NUMBER,
                countryCode = "+91"
            )
        }

        // 4. Check General Mobile Number
        val genMatch = generalMobilePattern.matchEntire(cleaned)
        if (genMatch != null) {
            val cc = genMatch.groupValues[1].takeIf { it.isNotEmpty() }?.let { "+$it" } ?: "+91"
            return ParsedSender(
                senderKind = SenderKind.PERSONAL_NUMBER,
                countryCode = cc
            )
        }

        // 5. Check Short Code
        if (shortCodePattern.matches(cleaned)) {
            return ParsedSender(
                senderKind = SenderKind.SHORT_CODE
            )
        }

        // 6. Generic Phone Number (starts with + or has 8+ digits)
        if (cleaned.startsWith("+") && cleaned.drop(1).all { it.isDigit() } && cleaned.length >= 8) {
            return ParsedSender(
                senderKind = SenderKind.PERSONAL_NUMBER,
                countryCode = if (cleaned.startsWith("+91")) "+91" else "+${cleaned.substring(1, minOf(4, cleaned.length))}"
            )
        }

        // 7. Otherwise, it is a Saved Contact or non-DLT alphanumeric name
        return ParsedSender(
            senderKind = SenderKind.SAVED_CONTACT
        )
    }
}
