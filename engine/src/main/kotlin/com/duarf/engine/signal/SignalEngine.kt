package com.duarf.engine.signal

import com.duarf.engine.extract.*
import com.duarf.engine.model.*
import com.duarf.engine.normalize.NormalizedText

data class FiredSignal(
    val signalId: String,
    val name: String,
    val weight: Double,
    val category: ScamCategory,
    val evidenceSpan: TextSpan?,
    val args: Map<String, String> = emptyMap(),
    val isFromContext: Boolean = false
)

data class FiredDampener(
    val signalId: String,
    val name: String,
    val factor: Double
)

class SignalEngine(
    private val ahoCorasick: AhoCorasick,
    private val shorteners: Set<String>,
    private val riskyTlds: Set<String>,
    private val remoteApps: Set<String>,
    private val blocklist: Set<String>
) {

    fun evaluate(
        message: IncomingMessage,
        normalized: NormalizedText,
        extracted: ExtractionResult,
        context: List<IncomingMessage> = emptyList(),
        isTrustedSender: Boolean = false,
        messageCountForSender: Int = 0
    ): Pair<List<FiredSignal>, List<FiredDampener>> {
        val signals = ArrayList<FiredSignal>()
        val dampeners = ArrayList<FiredDampener>()

        val isGroup = message.isGroup

        // 1. Sender & Context signals (§7.2 S01..S03)
        // In group chats S* signals and P10 are disabled (§10)
        val s01 = !isGroup && message.senderKind == SenderKind.NUMBER_ONLY
        if (s01) {
            signals.add(
                FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)
            )
        }

        val s02 = s01 && message.senderCountryCode != null && message.senderCountryCode != "+91"
        if (s02) {
            signals.add(
                FiredSignal(
                    "S02", "sender_foreign_number", 0.15, ScamCategory.OTHER_SUSPICIOUS, null,
                    mapOf("country" to (message.senderCountryCode ?: ""))
                )
            )
        }

        val s03 = !isGroup && (context.isEmpty() && messageCountForSender <= 1)
        if (s03 && s01) {
            signals.add(
                FiredSignal("S03", "first_contact", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)
            )
        }

        // 2. Links and files (§7.2 L01..L12)
        val apkFile = extracted.fileNames.firstOrNull { it.isApkFamily }
        val apkUrl = extracted.urls.firstOrNull { it.fileExtension == "apk" || it.fileExtension == "xapk" }
        if (apkFile != null || apkUrl != null) {
            val span = apkFile?.span ?: apkUrl?.span
            val fileName = apkFile?.fileName ?: apkUrl?.path?.substringAfterLast('/') ?: "app.apk"
            signals.add(
                FiredSignal(
                    "L01", "apk_file_or_link", 0.55, ScamCategory.MALICIOUS_APK, span,
                    mapOf("file" to fileName)
                )
            )
        }

        // Check each URL
        for (url in extracted.urls) {
            // L04: IP literal URL
            if (url.isIpLiteral) {
                signals.add(
                    FiredSignal("L04", "ip_literal_url", 0.35, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("host" to url.host))
                )
            }

            // L05: URL shortener
            val isShortener = shorteners.contains(url.registrableDomain) || shorteners.contains(url.host)
            if (isShortener) {
                signals.add(
                    FiredSignal("L05", "url_shortener", 0.20, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("domain" to url.registrableDomain))
                )
            }

            // L06: Risky TLD
            if (riskyTlds.contains(url.tld.lowercase())) {
                signals.add(
                    FiredSignal("L06", "risky_tld", 0.15, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("tld" to url.tld))
                )
            }

            // L07: Punycode or mixed script
            if (url.isPunycode) {
                signals.add(
                    FiredSignal("L07", "punycode_or_mixed_script_domain", 0.45, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("host" to url.host))
                )
            }

            // L08: Obfuscated URL
            if (url.isDeobfuscated) {
                signals.add(
                    FiredSignal("L08", "obfuscated_url", 0.35, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("url" to url.rawText))
                )
            }

            // L10: URL userinfo trick
            if (!url.userinfo.isNullOrEmpty()) {
                signals.add(
                    FiredSignal("L10", "url_userinfo_trick", 0.55, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("userinfo" to url.userinfo, "host" to url.host))
                )
            }

            // L11: Blocklisted domain
            if (blocklist.contains(url.registrableDomain) || blocklist.contains(url.host)) {
                signals.add(
                    FiredSignal("L11", "blocklisted_domain", 0.90, ScamCategory.PHISHING_BANK_KYC, url.span, mapOf("domain" to url.registrableDomain))
                )
            }

            // L12: Redirect to other chat from number-only sender
            val isChatRedirect = url.host == "wa.me" || url.host == "t.me" || url.host == "telegram.me"
            if (isChatRedirect && s01) {
                signals.add(
                    FiredSignal("L12", "redirect_to_other_chat", 0.20, ScamCategory.JOB_TASK, url.span, mapOf("service" to url.host))
                )
            }
        }

        // Brand + URL checks (L02, L03, L09)
        for (brand in extracted.brands) {
            val officialDomains = brand.officialDomains

            for (url in extracted.urls) {
                val isOfficial = officialDomains.any { it.equals(url.registrableDomain, ignoreCase = true) }

                // L02: Brand domain mismatch
                if (!isOfficial && officialDomains.isNotEmpty()) {
                    signals.add(
                        FiredSignal(
                            "L02", "brand_domain_mismatch", 0.50, ScamCategory.PHISHING_BANK_KYC, url.span,
                            mapOf("brand" to brand.brandName, "domain" to url.registrableDomain, "officialDomain" to officialDomains.first())
                        )
                    )
                }

                // L03: Lookalike domain (Levenshtein distance <= 2 on domain label, or contains brand inside non-official domain)
                if (!isOfficial) {
                    val domainPrefix = url.registrableDomain.substringBefore('.')
                    val containsBrandName = brand.brandName.lowercase().length >= 3 && url.host.contains(brand.brandName.lowercase())

                    var isLevenshteinClose = false
                    var matchedOfficial = ""
                    for (off in officialDomains) {
                        val offPrefix = off.substringBefore('.')
                        if (DamerauLevenshtein.distance(domainPrefix, offPrefix) in 1..2) {
                            isLevenshteinClose = true
                            matchedOfficial = off
                            break
                        }
                    }

                    if (isLevenshteinClose || containsBrandName) {
                        signals.add(
                            FiredSignal(
                                "L03", "lookalike_domain", 0.60, ScamCategory.PHISHING_BANK_KYC, url.span,
                                mapOf("brand" to brand.brandName, "domain" to url.registrableDomain, "officialDomain" to (matchedOfficial.ifEmpty { officialDomains.firstOrNull() ?: "" }))
                            )
                        )
                    }
                }

                // L09: Government claim on non-gov domain
                val isGov = brand.brandKind == BrandKind.GOVERNMENT || brand.brandKind == BrandKind.LAW_ENFORCEMENT
                val isGovDomain = url.registrableDomain.endsWith(".gov.in") || url.registrableDomain.endsWith(".nic.in")
                if (isGov && !isGovDomain) {
                    signals.add(
                        FiredSignal(
                            "L09", "gov_claim_non_gov_domain", 0.50, ScamCategory.AUTHORITY_DIGITAL_ARREST, url.span,
                            mapOf("brand" to brand.brandName, "domain" to url.registrableDomain)
                        )
                    )
                }
            }
        }

        // 3. Lexicon matching (§7.2 A* and P*)
        // Match both normalized and deobfuscated texts using AhoCorasick
        val normMatches = ahoCorasick.findMatches(normalized.normalizedText)
        val deobMatches = ahoCorasick.findMatches(normalized.deobfuscatedText)

        val allMatches = HashMap<String, LexiconMatch>()
        for (m in normMatches) allMatches[m.intent] = m
        for (m in deobMatches) {
            if (!allMatches.containsKey(m.intent)) {
                allMatches[m.intent] = m
            }
        }

        // Map matches to signals
        for ((intent, match) in allMatches) {
            val span = mapMatchToOriginalSpan(match, normalized)

            when (intent) {
                // Asks
                "asks_otp_pin_cvv" -> {
                    val isNegated = isNegatedOtpAsk(normalized.normalizedText, match) ||
                            isNegatedOtpAsk(normalized.deobfuscatedText, match)
                    if (!isNegated) {
                        signals.add(
                            FiredSignal("A01", "asks_otp_pin_cvv", 0.60, ScamCategory.OTP_ACCOUNT_TAKEOVER, span)
                        )
                    }
                }
                "asks_install_app" -> {
                    // Check if mentions remote apps specifically
                    val isRemote = remoteApps.any { normalized.normalizedText.contains(it) }
                    signals.add(
                        FiredSignal("A02", "asks_install_app", 0.55, if (isRemote) ScamCategory.REMOTE_ACCESS else ScamCategory.MALICIOUS_APK, span)
                    )
                }
                "asks_payment" -> signals.add(
                    FiredSignal("A03", "asks_payment", 0.35, ScamCategory.UPI_PAYMENT_FRAUD, span)
                )
                "upi_pin_to_receive" -> signals.add(
                    FiredSignal("A04", "upi_pin_to_receive", 0.65, ScamCategory.UPI_PAYMENT_FRAUD, span)
                )
                "asks_identity_details" -> signals.add(
                    FiredSignal("A05", "asks_identity_details", 0.35, ScamCategory.PHISHING_BANK_KYC, span)
                )
                "asks_secrecy_or_stay_on_call" -> signals.add(
                    FiredSignal("A06", "asks_secrecy_or_stay_on_call", 0.45, ScamCategory.AUTHORITY_DIGITAL_ARREST, span)
                )
                "asks_click_to_fix" -> signals.add(
                    FiredSignal("A07", "asks_click_to_fix", 0.20, ScamCategory.PHISHING_BANK_KYC, span)
                )
                "asks_move_platform" -> {
                    if (s01) {
                        signals.add(FiredSignal("A08", "asks_move_platform", 0.20, ScamCategory.JOB_TASK, span))
                    }
                }
                "money_from_new_number" -> {
                    if (s01) {
                        signals.add(FiredSignal("A09", "money_from_new_number", 0.40, ScamCategory.IMPERSONATED_CONTACT, span))
                    }
                }

                // Pressure and lures
                "urgency_deadline" -> signals.add(
                    FiredSignal("P01", "urgency_deadline", 0.20, ScamCategory.PHISHING_BANK_KYC, span)
                )
                "threat_account_block" -> {
                    if (!isAwarenessOrAdvisory(normalized.normalizedText, match)) {
                        signals.add(
                            FiredSignal("P02", "threat_account_block", 0.35, ScamCategory.PHISHING_BANK_KYC, span)
                        )
                    }
                }
                "threat_legal_arrest" -> {
                    if (!isAwarenessOrAdvisory(normalized.normalizedText, match)) {
                        signals.add(
                            FiredSignal("P03", "threat_legal_arrest", 0.50, ScamCategory.AUTHORITY_DIGITAL_ARREST, span)
                        )
                    }
                }
                "threat_utility_disconnect" -> {
                    if (!isAwarenessOrAdvisory(normalized.normalizedText, match)) {
                        signals.add(
                            FiredSignal("P04", "threat_utility_disconnect", 0.45, ScamCategory.UTILITY_DISCONNECT, span)
                        )
                    }
                }
                "lure_prize_lottery" -> signals.add(
                    FiredSignal("P05", "lure_prize_lottery", 0.40, ScamCategory.LOTTERY_PRIZE, span)
                )
                "lure_job_task" -> signals.add(
                    FiredSignal("P06", "lure_job_task", 0.40, ScamCategory.JOB_TASK, span)
                )
                "lure_investment" -> signals.add(
                    FiredSignal("P07", "lure_investment", 0.40, ScamCategory.INVESTMENT_TRADING, span)
                )
                "lure_instant_loan" -> signals.add(
                    FiredSignal("P08", "lure_instant_loan", 0.25, ScamCategory.LOAN_CREDIT, span)
                )
                "lure_refund_cashback" -> signals.add(
                    FiredSignal("P09", "lure_refund_cashback", 0.30, ScamCategory.UPI_PAYMENT_FRAUD, span)
                )
                "delivery_failed" -> signals.add(
                    FiredSignal("P11", "delivery_failed", 0.30, ScamCategory.DELIVERY_COURIER, span)
                )
                "traffic_challan" -> signals.add(
                    FiredSignal("P12", "traffic_challan", 0.25, ScamCategory.MALICIOUS_APK, span)
                )
                "invitation_lure" -> signals.add(
                    FiredSignal("P13", "invitation_lure", 0.10, ScamCategory.MALICIOUS_APK, span)
                )
                "generic_mass_greeting" -> signals.add(
                    FiredSignal("P14", "generic_mass_greeting", 0.10, ScamCategory.OTHER_SUSPICIOUS, span)
                )
            }
        }

        // P10: Impersonates institution
        // A BANK, GOVERNMENT, LAW_ENFORCEMENT, TELECOM, COURIER or UTILITY brand named by a number-only sender (in non-group)
        if (s01 && extracted.brands.isNotEmpty()) {
            val institutionalBrand = extracted.brands.firstOrNull {
                it.brandKind != BrandKind.ECOMMERCE && it.brandKind != BrandKind.PAYMENTS
            }
            if (institutionalBrand != null) {
                signals.add(
                    FiredSignal(
                        "P10", "impersonates_institution", 0.20, institutionalBrand.brandKind.toCategory(),
                        institutionalBrand.span, mapOf("brand" to institutionalBrand.brandName)
                    )
                )
            }
        }

        // 4. Text hygiene (§7.2 T01)
        if (normalized.hadInvisibleChars || normalized.hadMixedScriptToken) {
            signals.add(
                FiredSignal("T01", "hidden_or_homoglyph_text", 0.30, ScamCategory.OTHER_SUSPICIOUS, null)
            )
        }

        // 5. Dampeners (§7.2 B01..B04)
        // Hard signals: L01, L10, L11, A01, A02, A04
        val hasHardSignal = signals.any { it.signalId in setOf("L01", "L10", "L11", "A01", "A02", "A04") }
        if (!hasHardSignal) {
            // B01: OTP delivery only: delivers code, no URL, no asks, contains "do not share"
            val hasOtpCode = extracted.otpCodes.isNotEmpty()
            val hasUrls = extracted.urls.isNotEmpty()
            val hasAsks = signals.any { it.signalId.startsWith("A") }
            val hasDoNotShare = normalized.normalizedText.contains("do not share") ||
                    normalized.normalizedText.contains("never share") ||
                    normalized.normalizedText.contains("don't share") ||
                    normalized.normalizedText.contains("dont share") ||
                    normalized.normalizedText.contains("साझा न करें") ||
                    normalized.normalizedText.contains("साझा न") ||
                    normalized.normalizedText.contains("न बताएं") ||
                    normalized.normalizedText.contains("मत बताएं") ||
                    normalized.normalizedText.contains("share na kare") ||
                    normalized.normalizedText.contains("share na") ||
                    normalized.normalizedText.contains("mat bata") ||
                    normalized.normalizedText.contains("mat batana")

            if (hasOtpCode && !hasUrls && !hasAsks && hasDoNotShare) {
                dampeners.add(FiredDampener("B01", "otp_delivery_only", 0.40))
            }
        }

        // B02: Official domains only
        if (extracted.urls.isNotEmpty()) {
            val allUrlsOfficial = extracted.urls.all { url ->
                extracted.brands.any { b -> b.officialDomains.contains(url.registrableDomain) }
            }
            if (allUrlsOfficial) {
                dampeners.add(FiredDampener("B02", "official_domains_only", 0.30))
            }
        }

        // B03: Established conversation (named sender and 20+ messages)
        if (message.senderKind == SenderKind.NAMED && messageCountForSender >= 20) {
            dampeners.add(FiredDampener("B03", "established_conversation", 0.15))
        }

        // B04: User trusted sender
        if (isTrustedSender) {
            dampeners.add(FiredDampener("B04", "user_trusted_sender", 0.50))
        }

        return Pair(signals, dampeners)
    }

    private fun isNegatedOtpAsk(text: String, match: LexiconMatch): Boolean {
        // 1. Check for evasion directives in the surrounding window (+/- 60 chars) or text
        val windowStart = (match.start - 60).coerceAtLeast(0)
        val windowEnd = (match.end + 60).coerceAtMost(text.length)
        val surrounding = text.substring(windowStart, windowEnd).lowercase()

        val evasionDirectives = listOf(
            "to me", "send me", "forward me", "tell me", "give me", "share with me", "send to me",
            "to our agent", "to officer", "on this number", "to this number", "here", "reply with",
            "मुझे", "हमे", "हमारे", "इस नंबर पर", "यहाँ", "अधिकारी को",
            "mujhe", "hame", "hamare", "is number", "yahan", "yaha", "agent ko", "officer ko"
        )
        if (evasionDirectives.any { surrounding.contains(it) }) {
            // Evasion attempt detected: requesting to send OTP to the speaker
            return false
        }

        // 2. Scope negation to the specific clause containing the match
        // Clauses are delimited by punctuation: . , ; ! ? : \n
        val delimiters = charArrayOf('.', ',', ';', '!', '?', ':', '\n')
        val textBefore = text.substring(0, match.start)
        val textAfter = text.substring(match.end)

        val lastDelim = textBefore.indexOfLast { it in delimiters }
        val clauseStart = if (lastDelim >= 0) lastDelim + 1 else 0

        val nextDelim = textAfter.indexOfFirst { it in delimiters }
        val clauseEnd = if (nextDelim >= 0) match.end + nextDelim else text.length

        val clause = text.substring(clauseStart, clauseEnd).lowercase()

        // Also check if conjunctions split the clause
        val subClauses = clause.split(Regex("""\b(but|except|however|lekin|magar|par|aur|and)\b"""))
        val relMatchStart = match.start - clauseStart
        var currentOffset = 0
        var targetSubClause = clause
        for (sc in subClauses) {
            val scEnd = currentOffset + sc.length
            if (relMatchStart in currentOffset..scEnd) {
                targetSubClause = sc
                break
            }
            currentOffset = scEnd + 1
        }

        val negationIndicators = listOf(
            "do not", "don't", "dont", "never", "not to share", "not share", "should not",
            "साझा न", "साझा मत", "न बताएं", "मत बताएं", "न दें", "मत दें", "नहीं दें", "किसी को न", "किसी के साथ न",
            "share na", "mat bata", "mat dena", "mat share", "kisi ko mat", "kisi ke sath na", "kisi se share na"
        )

        return negationIndicators.any { targetSubClause.contains(it) }
    }

    private fun isAwarenessOrAdvisory(text: String, match: LexiconMatch): Boolean {
        val windowStart = (match.start - 60).coerceAtLeast(0)
        val windowEnd = (match.end + 60).coerceAtMost(text.length)
        val surrounding = text.substring(windowStart, windowEnd).lowercase()

        val indicators = listOf(
            "warns against", "warn against", "warning", "advisory", "beware", "fake", "do not fall for",
            "चेतावनी", "सावधान", "अलर्ट", "फर्जी", "बचें", "धोखाधड़ी",
            "fraud se bache", "fraud alert", "savdhan", "fake hai", "satark rahe"
        )
        return indicators.any { surrounding.contains(it) } ||
                text.lowercase().contains("security advisory") ||
                text.lowercase().contains("cyber police warns") ||
                text.lowercase().contains("police warns")
    }

    private fun mapMatchToOriginalSpan(match: LexiconMatch, normalized: NormalizedText): TextSpan {
        val map = normalized.indexMap
        if (map.isEmpty()) return TextSpan(0, 0)
        val s = if (match.start in map.indices) map[match.start] else 0
        val e = if (match.end - 1 in map.indices) map[match.end - 1] + 1 else s + (match.end - match.start)
        return TextSpan(s, maxOf(s, e))
    }

    private fun BrandKind.toCategory(): ScamCategory = when (this) {
        BrandKind.BANK -> ScamCategory.PHISHING_BANK_KYC
        BrandKind.GOVERNMENT, BrandKind.LAW_ENFORCEMENT -> ScamCategory.AUTHORITY_DIGITAL_ARREST
        BrandKind.UTILITY -> ScamCategory.UTILITY_DISCONNECT
        BrandKind.COURIER -> ScamCategory.DELIVERY_COURIER
        BrandKind.TELECOM -> ScamCategory.PHISHING_BANK_KYC
        else -> ScamCategory.OTHER_SUSPICIOUS
    }
}
