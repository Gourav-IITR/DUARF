// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind {
    NOTIFICATION,
    SHARE,
    PASTE
}

@Serializable
enum class SourceApp {
    WHATSAPP,
    WHATSAPP_BUSINESS,
    SMS_GOOGLE_MESSAGES,
    SMS_SAMSUNG_MESSAGES,
    SMS_GENERIC,
    UNKNOWN;

    val isSms: Boolean
        get() = this == SMS_GOOGLE_MESSAGES || this == SMS_SAMSUNG_MESSAGES || this == SMS_GENERIC
}

@Serializable
enum class SenderKind {
    NUMBER_ONLY,
    NAMED,
    UNKNOWN,
    DLT_HEADER,
    PERSONAL_NUMBER,
    SHORT_CODE,
    SAVED_CONTACT
}

@Serializable
data class IncomingMessage(
    val fingerprint: String,          // SHA-256
    val source: SourceKind,
    val app: SourceApp,
    val conversationKey: String?,     // HMAC of a stable chat id; null for SHARE/PASTE
    val senderDisplay: String?,       // as shown in the notification; RAM only unless alert persists
    val senderKind: SenderKind,
    val senderCountryCode: String?,   // "+91", "+92", ... when senderKind == NUMBER_ONLY or PERSONAL_NUMBER
    val isGroup: Boolean,
    val text: String,
    val attachmentHint: String?,      // e.g. document file name surfaced in notification
    val receivedAtMillis: Long,
    val dltHeaderPrefix: String? = null, // e.g. "AX", "VM", "JD"
    val dltHeaderBrand: String? = null,  // e.g. "HDFCBK", "SBIBNK"
    val dltHeaderSuffix: String? = null  // "P", "S", "T", "G"
)

@Serializable
enum class AlertLevel {
    NONE,
    CAUTION,
    DANGER
}

@Serializable
enum class ScamCategory {
    MALICIOUS_APK,
    PHISHING_BANK_KYC,
    AUTHORITY_DIGITAL_ARREST,
    UTILITY_DISCONNECT,
    UPI_PAYMENT_FRAUD,
    OTP_ACCOUNT_TAKEOVER,
    REMOTE_ACCESS,
    JOB_TASK,
    INVESTMENT_TRADING,
    LOTTERY_PRIZE,
    LOAN_CREDIT,
    DELIVERY_COURIER,
    IMPERSONATED_CONTACT,
    OTHER_SUSPICIOUS
}

@Serializable
data class TextSpan(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "start must be >= 0 (got $start)" }
        require(end >= start) { "end must be >= start (got start=$start, end=$end)" }
    }
}

@Serializable
data class Reason(
    val signalId: String,
    val titleKey: String,            // string-resource key, localized in :app
    val detailKey: String,
    val args: Map<String, String> = emptyMap(),   // e.g. domain, brand, file name
    val evidence: TextSpan? = null,
)

@Serializable
data class Verdict(
    val level: AlertLevel,
    val score: Double,               // fused, 0..1
    val ruleScore: Double,
    val modelProbability: Double?,   // null if model unavailable
    val category: ScamCategory,
    val reasons: List<Reason>,       // ordered by weight, max 3 shown
    val highlights: List<TextSpan>,  // offsets into the ORIGINAL text
    val engineVersion: String,       // packs version + model version
)

enum class Sensitivity {
    LOW,
    BALANCED,
    HIGH
}

interface ScamEngine {
    fun analyze(
        message: IncomingMessage,
        context: List<IncomingMessage> = emptyList(),
        sensitivity: Sensitivity = Sensitivity.BALANCED
    ): Verdict

    val isModelLoaded: Boolean get() = false
    val modelVersion: Int? get() = null
}
