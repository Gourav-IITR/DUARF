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
    val isFromContext: Boolean = false,
    val allEvidenceSpans: List<TextSpan> = listOfNotNull(evidenceSpan)
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
    private val blocklist: Set<String>,
    private val policeDltHeaders: Set<String> = emptySet()
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
        val s01 = !isGroup && (message.senderKind == SenderKind.NUMBER_ONLY || message.senderKind == SenderKind.PERSONAL_NUMBER)
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

        val s03 = message.source == SourceKind.NOTIFICATION &&
                message.conversationKey != null &&
                !isGroup && (context.isEmpty() && messageCountForSender <= 1)
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
                val isOfficial = officialDomains.any {
                    it.equals(url.registrableDomain, ignoreCase = true) ||
                    it.equals(url.host, ignoreCase = true) ||
                    url.host.endsWith(".$it", ignoreCase = true)
                }

                // L02: Brand domain mismatch
                if (brand.isVerified && !isOfficial && officialDomains.isNotEmpty()) {
                    signals.add(
                        FiredSignal(
                            "L02", "brand_domain_mismatch", 0.50, ScamCategory.PHISHING_BANK_KYC, url.span,
                            mapOf("brand" to brand.brandName, "domain" to url.registrableDomain, "officialDomain" to officialDomains.first())
                        )
                    )
                }

                // L03: Lookalike domain (Levenshtein distance <= 2 on domain label, or contains brand inside non-official domain)
                if (brand.isVerified && !isOfficial) {
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
        for (m in normMatches) {
            val existing = allMatches[m.intent]
            if (existing == null || (m.end - m.start) > (existing.end - existing.start)) {
                allMatches[m.intent] = m
            }
        }
        for (m in deobMatches) {
            val existing = allMatches[m.intent]
            if (existing == null || (m.end - m.start) > (existing.end - existing.start)) {
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
                "asks_identity_details" -> signals.add(
                    FiredSignal("A05", "asks_identity_details", 0.35, ScamCategory.PHISHING_BANK_KYC, span)
                )
                "asks_secrecy_or_stay_on_call" -> {
                    if (message.senderKind != SenderKind.DLT_HEADER) {
                        signals.add(
                            FiredSignal("A06", "asks_secrecy_or_stay_on_call", 0.45, ScamCategory.AUTHORITY_DIGITAL_ARREST, span)
                        )
                    }
                }
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
                    if (isDirectedThreat(normalized.normalizedText, normalized.deobfuscatedText, match)) {
                        signals.add(
                            FiredSignal("P02", "threat_account_block", 0.35, ScamCategory.PHISHING_BANK_KYC, span)
                        )
                    }
                }
                "threat_legal_arrest" -> {
                    if (isDirectedThreat(normalized.normalizedText, normalized.deobfuscatedText, match)) {
                        signals.add(
                            FiredSignal("P03", "threat_legal_arrest", 0.50, ScamCategory.AUTHORITY_DIGITAL_ARREST, span)
                        )
                    }
                }
                "threat_utility_disconnect" -> {
                    if (isDirectedThreat(normalized.normalizedText, normalized.deobfuscatedText, match)) {
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

        // A04: upi_pin_to_receive with proximity rule (instruction + receive lure <= 8 tokens) and scoped negation
        val a04Signal = evaluateA04(
            normalized = normalized,
            normMatches = normMatches,
            deobMatches = deobMatches
        )
        if (a04Signal != null) {
            signals.add(a04Signal)
        }

        // Guard condition: awareness context suppresses S04, P10, and S05 only when no A*, L*, P02-P04 fires
        val hasHardOrAskOrLink = signals.any {
            it.signalId.startsWith("A") || it.signalId.startsWith("L") || it.signalId in setOf("P02", "P03", "P04")
        }

        // S04: institution_claim_from_personal_number (SMS only)
        // Bank/gov/utility/courier/telecom brand claimed by PERSONAL_NUMBER or NUMBER_ONLY
        val isSms = message.app.isSms
        if (isSms && (message.senderKind == SenderKind.PERSONAL_NUMBER || message.senderKind == SenderKind.NUMBER_ONLY) && extracted.brands.isNotEmpty()) {
            val textNorm = normalized.normalizedText
            val textDeob = normalized.deobfuscatedText
            val institutionalBrand = extracted.brands.firstOrNull { brand ->
                brand.brandKind in setOf(
                    BrandKind.BANK, BrandKind.GOVERNMENT, BrandKind.LAW_ENFORCEMENT,
                    BrandKind.UTILITY, BrandKind.COURIER, BrandKind.TELECOM
                )
            }
            if (institutionalBrand != null) {
                val isAwareness = (isAwarenessOrAdvisory(textNorm, institutionalBrand.span) ||
                        isAwarenessOrAdvisory(textDeob, institutionalBrand.span)) && !hasHardOrAskOrLink
                if (!isAwareness) {
                    signals.add(
                        FiredSignal(
                            "S04", "institution_claim_from_personal_number", 0.50,
                            institutionalBrand.brandKind.toCategory(), institutionalBrand.span,
                            mapOf("brand" to institutionalBrand.brandName)
                        )
                    )
                }
            }
        }

        // P10: Impersonates institution
        // A BANK, GOVERNMENT, LAW_ENFORCEMENT, TELECOM, COURIER or UTILITY brand named by a number-only sender (in non-group)
        // Must require an actual impersonation claim and is suppressed by awareness/advisory context only when no A*, L*, P02-P04 fires
        if (s01 && extracted.brands.isNotEmpty()) {
            val textNorm = normalized.normalizedText
            val textDeob = normalized.deobfuscatedText
            val institutionalBrand = extracted.brands.firstOrNull { brand ->
                brand.brandKind != BrandKind.ECOMMERCE && brand.brandKind != BrandKind.PAYMENTS &&
                        hasImpersonationClaim(textNorm, textDeob, brand, allMatches)
            }
            if (institutionalBrand != null) {
                val isAwareness = (isAwarenessOrAdvisory(textNorm, institutionalBrand.span) ||
                        isAwarenessOrAdvisory(textDeob, institutionalBrand.span)) && !hasHardOrAskOrLink
                if (!isAwareness) {
                    signals.add(
                        FiredSignal(
                            "P10", "impersonates_institution", 0.20, institutionalBrand.brandKind.toCategory(),
                            institutionalBrand.span, mapOf("brand" to institutionalBrand.brandName)
                        )
                    )
                }
            }
        }

        // S05: header_claim_mismatch (SMS only)
        // Either:
        // 1. Claimed institutional brand does not match DLT header brand
        // 2. A "-P" (promotional) header asks for OTP, KYC, or payment
        if (isSms && message.senderKind == SenderKind.DLT_HEADER) {
            val textNorm = normalized.normalizedText
            val textDeob = normalized.deobfuscatedText
            val isAwareness = (isAwarenessOrAdvisory(textNorm) || isAwarenessOrAdvisory(textDeob)) && !hasHardOrAskOrLink
            if (!isAwareness) {
                // Condition 1: Claimed brand doesn't match DLT header
                val mismatchedBrand = extracted.brands.firstOrNull { brand ->
                    brand.brandKind in setOf(
                        BrandKind.BANK, BrandKind.GOVERNMENT, BrandKind.LAW_ENFORCEMENT,
                        BrandKind.UTILITY, BrandKind.COURIER, BrandKind.TELECOM
                    ) && !matchesDltBrand(message.dltHeaderBrand, brand)
                }
                if (mismatchedBrand != null) {
                    signals.add(
                        FiredSignal(
                            "S05", "header_claim_mismatch", 0.55,
                            mismatchedBrand.brandKind.toCategory(), mismatchedBrand.span,
                            mapOf("brand" to mismatchedBrand.brandName, "header" to (message.dltHeaderBrand ?: ""))
                        )
                    )
                } else if (message.dltHeaderSuffix == "P") {
                    // Condition 2: Promotional header asking for OTP, KYC, or payment
                    val hasSensitiveAsk = signals.any { it.signalId in setOf("A01", "A03", "A04", "A05", "P02") }
                    if (hasSensitiveAsk) {
                        signals.add(
                            FiredSignal(
                                "S05", "header_claim_mismatch", 0.55,
                                ScamCategory.PHISHING_BANK_KYC, null,
                                mapOf("suffix" to "P")
                            )
                        )
                    }
                }
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
                extracted.brands.any { b ->
                    b.isVerified && b.officialDomains.any { off ->
                        off.equals(url.registrableDomain, ignoreCase = true) ||
                        off.equals(url.host, ignoreCase = true) ||
                        url.host.endsWith(".$off", ignoreCase = true)
                    }
                }
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

        // B05: Awareness / advisory context without hard or link signals, asks, threats, or payment
        val isAwareness = isAwarenessOrAdvisory(normalized.normalizedText) ||
                isAwarenessOrAdvisory(normalized.deobfuscatedText)
        val hasDisqualifyingSignal = signals.any { signal ->
            signal.signalId.startsWith("A") ||
            signal.signalId.startsWith("L") ||
            signal.signalId in setOf("P02", "P03", "P04")
        }
        if (isAwareness && !hasDisqualifyingSignal) {
            dampeners.add(FiredDampener("B05", "awareness_or_advisory_context", 0.60))
        }

        // B06: verified_header_consistent (SMS only)
        // -T/-S/-G header whose brand matches claimed brand with no link mismatch or ask. Dampener. Never applies on hard signals.
        if (isSms && !hasHardSignal && message.senderKind == SenderKind.DLT_HEADER && message.dltHeaderSuffix in setOf("T", "S", "G")) {
            val hasDisqualifyingForB06 = signals.any { signal ->
                signal.signalId.startsWith("A") ||
                signal.signalId in setOf("L02", "L03", "L04", "L07", "L08", "L09", "L10", "L11", "P02", "P03", "P04", "S04", "S05")
            }
            val hasMismatchedBrand = extracted.brands.any { brand ->
                brand.brandKind in setOf(
                    BrandKind.BANK, BrandKind.GOVERNMENT, BrandKind.LAW_ENFORCEMENT,
                    BrandKind.UTILITY, BrandKind.COURIER, BrandKind.TELECOM
                ) && (!matchesDltBrand(message.dltHeaderBrand, brand) || !brand.isVerified)
            }
            val hasUnverifiedBrand = extracted.brands.any { !it.isVerified }
            if (!hasDisqualifyingForB06 && !hasMismatchedBrand && !hasUnverifiedBrand) {
                dampeners.add(FiredDampener("B06", "verified_header_consistent", 0.50))
            }
        }

        return Pair(deduplicateSignals(signals), dampeners)
    }

    private fun evaluateA04(
        normalized: NormalizedText,
        normMatches: List<LexiconMatch>,
        deobMatches: List<LexiconMatch>
    ): FiredSignal? {
        val textNorm = normalized.normalizedText
        val textDeob = normalized.deobfuscatedText

        val allRawMatches = normMatches + deobMatches

        // 1. Direct matches for upi_pin_to_receive
        val directMatches = allRawMatches.filter { it.intent == "upi_pin_to_receive" }
        for (m in directMatches) {
            if (!isNegatedUpiPinAsk(textNorm, textDeob, m.start, m.end, allRawMatches)) {
                val span = mapMatchToOriginalSpan(m, normalized)
                return FiredSignal("A04", "upi_pin_to_receive", 0.65, ScamCategory.UPI_PAYMENT_FRAUD, span)
            }
        }

        // 2. Proximity matches: instruction (upi_pin_instruction) + lure (receive_money_lure) <= 8 tokens
        val instructions = allRawMatches.filter { it.intent == "upi_pin_instruction" }
        val receiveLures = allRawMatches.filter { it.intent == "receive_money_lure" }

        for (instr in instructions) {
            if (isNegatedUpiPinAsk(textNorm, textDeob, instr.start, instr.end, allRawMatches)) {
                continue
            }

            for (lure in receiveLures) {
                val (first, second) = if (instr.start <= lure.start) Pair(instr, lure) else Pair(lure, instr)
                val intermediate = textNorm.substring(
                    first.end.coerceAtMost(textNorm.length),
                    second.start.coerceAtMost(textNorm.length).coerceAtLeast(first.end)
                )
                val tokenCount = intermediate.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                if (tokenCount <= 8) {
                    val combinedMatch = LexiconMatch(
                        "upi_pin_to_receive",
                        textNorm.substring(first.start, second.end.coerceAtMost(textNorm.length)),
                        first.start,
                        second.end.coerceAtMost(textNorm.length)
                    )
                    val span = mapMatchToOriginalSpan(combinedMatch, normalized)
                    return FiredSignal("A04", "upi_pin_to_receive", 0.65, ScamCategory.UPI_PAYMENT_FRAUD, span)
                }
            }
        }

        return null
    }

    private fun isNegatedUpiPinAsk(
        textNorm: String,
        textDeob: String,
        matchStart: Int,
        matchEnd: Int,
        allMatches: List<LexiconMatch>
    ): Boolean {
        // 1. Check if an explicit negation match (negation_upi_pin_receive) covers or is adjacent to this span
        val negations = allMatches.filter { it.intent == "negation_upi_pin_receive" }
        for (neg in negations) {
            val overlaps = (neg.start <= matchEnd && neg.end >= matchStart)
            val nearby = Math.abs(neg.start - matchStart) < 60 || Math.abs(neg.end - matchEnd) < 60
            if (overlaps || nearby) {
                val minStart = minOf(neg.start, matchStart)
                val maxEnd = maxOf(neg.end, matchEnd)
                val intermediate = textNorm.substring(minStart, maxEnd.coerceAtMost(textNorm.length))
                if (!intermediate.contains('\n') && !intermediate.contains(';') && intermediate.count { it == '.' } <= 1) {
                    return true
                }
            }
        }

        // 2. Scoped regex checks on the surrounding clause (within sentence or line bounds)
        fun checkRegexOnText(text: String): Boolean {
            val clauseStart = maxOf(0, text.lastIndexOfAny(charArrayOf('.', '\n', ';', '!'), (matchStart - 1).coerceAtLeast(0)) + 1)
            val clauseEnd = text.indexOfAny(charArrayOf('.', '\n', ';', '!'), matchEnd).let { if (it == -1) text.length else it }
            val clause = text.substring(clauseStart, clauseEnd).lowercase()

            val pinNegationPatterns = listOf(
                Regex("""\b(never|do\s+not|don'?t)\s+(ever\s+)?(need|enter|share|give|provide|use)\b.*?\bpin\b"""),
                Regex("""\b(no|not)\b.*?\bpin\b.*?\b(needed|required)\b"""),
                Regex("""\bpin\b.*?\b(is\s+)?(not|never)\b.*?\b(needed|required)\b"""),
                Regex("""\b(never|do\s+not|don'?t)\s+need\b.*?\bpin\b"""),
                Regex("""\bpin\b.*?\b(is\s+)?only\b.*?\b(send|sending)\b"""),
                Regex("""\b(kabhi\s+bhi\s+|kabhi\s+)?(mat\s+daal|mat\s+dalo|na\s+dale|share\s+na\s+kare)\b.*?\bpin\b"""),
                Regex("""\bpin\b.*?\b(mat\s+daal|mat\s+dalo|na\s+dale|share\s+na\s+kare)\b"""),
                Regex("""\bpin\b.*?\b(ki\s+)?(zarurat|zaroorat|jarurat|aavashyakta|avashyakta)\s+nahi\b"""),
                Regex("""\bpin\b.*?\b(sirf|keval)\b.*?\bbhejne\b"""),
                Regex("""\bpin\b.*?\bnahi\s+(lagta|lagega|chahiye)\b"""),
                Regex("""पिन.*(आवश्यकता नहीं|जरूरत नहीं|नहीं लगता|नहीं लगेगा|दर्ज न करें|साझा न करें|केवल भेजने|सिर्फ भेजने)"""),
                Regex("""পিন.*(প্রয়োজন নেই|লাগে না|দেবেন না|শুধুমাত্র টাকা পাঠানোর জন্য)"""),
                Regex("""पिन.*(गरज नाही|आवश्यक नाही|लागत नाही|फक्त पैसे पाठवण्यासाठी)"""),
                Regex("""పిన్.*(అవసరం లేదు|కేవలం డబ్బులు పంపడానికి)"""),
                Regex("""பின்.*(தேவையில்லை|பணம் அனுப்ப மட்டுமே)"""),
                Regex("""ପିନ୍.*(ଦରକାର ନାହିଁ|ପଠାଇବା ପାଇଁ)"""),
                Regex("""પીન.*(જરૂર નથી|નાખવો પડતો નથી|માત્ર પૈસા મોકલવા|ફક્ત પૈસા મોકલવા)"""),
                Regex("""ಪಿನ್.*(ಅಗತ್ಯವಿಲ್ಲ|ಹಾಕುವಂತಿಲ್ಲ|ಕೇವಲ ಹಣ ಕಳುಹಿಸಲು|ಮಾತ್ರ)"""),
                Regex("""പിൻ.*(ആവശ്യമില്ല|അടിക്കേണ്ടതില്ല|പണം അയക്കാൻ മാത്രമാണ്)"""),
                Regex("""ਪਿੰਨ.*(ਲੋੜ ਨਹੀਂ|ਨਹੀਂ ਲੱਗਦਾ|ਸਿਰਫ਼ ਪੈਸੇ ਭੇਜਣ)""")
            )

            return pinNegationPatterns.any { it.containsMatchIn(clause) }
        }

        return checkRegexOnText(textNorm) || checkRegexOnText(textDeob)
    }

    private fun deduplicateSignals(signals: List<FiredSignal>): List<FiredSignal> {
        if (signals.size <= 1) return signals
        val grouped = LinkedHashMap<String, MutableList<FiredSignal>>()
        for (s in signals) {
            grouped.getOrPut(s.signalId) { mutableListOf() }.add(s)
        }
        val result = ArrayList<FiredSignal>(grouped.size)
        for ((_, list) in grouped) {
            if (list.size == 1) {
                result.add(list[0])
            } else {
                val highest = list.maxByOrNull { it.weight } ?: list[0]
                val primarySpan = highest.evidenceSpan ?: list.firstOrNull { it.evidenceSpan != null }?.evidenceSpan
                val allSpans = list.flatMap { it.allEvidenceSpans.ifEmpty { listOfNotNull(it.evidenceSpan) } }.distinct()
                result.add(highest.copy(evidenceSpan = primarySpan, allEvidenceSpans = allSpans))
            }
        }
        return result
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
            "share na", "mat bata", "mat dena", "mat share", "kisi ko mat", "kisi ke sath na", "kisi se share na",
            "শেয়ার করবেন না", "বলবেন না", "দেবেন না", "কারোর সাথে নয়",
            "शेअर करू नका", "सांगू नका", "देऊ नका", "कोणालाही नाही", "कोणाशीही शेअर",
            "పంచుకోవద్దు", "చెప్పవద్దు", "ఇవ్వవద్దు", "ఎవరితోనూ కాదు",
            "பகிர வேண்டாம்", "சொல்ல வேண்டாம்", "கொடுக்க வேண்டாம்", "யாருடனும்",
            "ସେୟାର କରନ୍ତୁ ନାହିଁ", "କୁହନ୍ତୁ ନାହିଁ", "ଦିଅନ୍ତୁ ନାହିଁ", "କାହା ସହିତ ନୁହେଁ"
        )

        return negationIndicators.any { targetSubClause.contains(it) }
    }

    private fun isAwarenessOrAdvisory(text: String, span: TextSpan? = null): Boolean {
        val lower = text.lowercase()
        val globalIndicators = listOf(
            "beware", "fraudster", "fraudsters", "security advisory", "cyber police warns",
            "police warns", "warns against", "warn against", "fraud alert", "scam alert",
            "do not fall for", "never install", "never share", "never enter", "do not enter",
            "bank alert", "bank advisory", "rbi kehta hai",
            "सावधान रहें", "सतर्क रहें", "satark rahe", "savdhan rahe", "fraud se bache", "scam se bache",
            "dhokhadhadi se bache", "scam awareness", "security alert", "public advisory",
            "police advisory", "cyber police advisory", "cyber cell warns", "cyber police alert",
            "forwarded for awareness", "forwarding for awareness", "awareness forward", "scam warning",
            "सुरक्षा चेतावनी", "साइबर पुलिस चेतावनी", "ठगों से सावधान", "धोखेबाजों से सावधान",
            "सुरक्षा सलाह", "सुरक्षा सूचना", "बैंक सुरक्षा", "आरबीआई कहता है",
            "किसी को मत देना", "kisi ko mat dena", "bank never asks", "bank kabhi nahi", "bank will never ask",
            "पुलिस एडवाइजरी", "एडवाइजरी", "चेतावनी संदेश",
            "সতর্ক থাকুন", "সাবধান থাকুন", "সতর্কবার্তা", "ব্যাংক নিরাপত্তা",
            "सतर्क राहा", "सावध राहा", "सुरक्षा संदेश", "बँक सुरक्षा",
            "అప్రమత్తంగా ఉండండి", "జాగ్రత్తగా ఉండండి", "హెచ్చరిక", "బ్యాంక్ భద్రతా",
            "விழிப்புடன் இருங்கள்", "எச்சரிக்கையாக இருங்கள்", "பாதுகாப்பு எச்சரிக்கை", "வங்கி பாதுகாப்பு",
            "ସତର୍କ ରୁହନ୍ତୁ", "ସାବଧାନ ରୁହନ୍ତୁ", "ସୁରକ୍ଷା ଚେତାବନୀ", "ବ୍ୟାଙ୍କ ସୁରକ୍ଷା",
            "સાવચેત રહો", "સાવધાન રહો", "સુરક્ષા સલાહ", "બેંક સુરક્ષા",
            "ಜಾಗರೂಕರಾಗಿರಿ", "ಎಚ್ಚರವಾಗಿರಿ", "ಸುರಕ್ಷತಾ ಎಚ್ಚರಿಕೆ", "ಬ್ಯಾಂಕ್ ಭದ್ರತೆ",
            "ജാഗ്രത പാലിക്കുക", "ശ്രദ്ധിക്കുക", "സുരക്ഷാ മുന്നറിയിപ്പ്", "ബാങ്ക് സുരക്ഷ",
            "ਸੁਚੇਤ ਰਹੋ", "ਸਾਵਧਾਨ ਰਹੋ", "ਸੁਰੱਖਿਆ ਸਲਾਹ", "ਬੈਂਕ ਸੁਰੱਖਿਆ",
            "সাইবার নিরাপত্তা", "নিরাপত্তা বিজ্ঞপ্তি", "সাইবার হেল্পলাইন", "সাইবার পুলিশ",
            "सायबर सुरक्षा", "सुरक्षा सूचना", "सायबर हेल्पलाइन", "सायबर पोलिस",
            "సైబర్ భద్రత", "భద్రతా నోటీసు", "సైబర్ హెల్ప్‌లైన్", "సైబర్ పోలీస్",
            "சைபர் பாதுகாப்பு", "பாதுகாப்பு அறிவிப்பு", "சைபர் உதவி எண்", "சைபர் காவல்துறை",
            "ସାଇବର ସୁରକ୍ଷା", "ସୁରକ୍ଷା ସୂଚନା", "ସାଇବର ହେଲ୍ପଲାଇନ୍", "ସାଇବର ପୋଲିସ",
            "સાયબર સુરક્ષા", "સુરક્ષા સૂચના", "સાયબર હેલ્પલાઇન", "સાયબર પોલીસ",
            "ಸೈಬರ್ ಸುರಕ್ಷತೆ", "ಸುರಕ್ಷತಾ ಸೂಚನೆ", "ಸೈಬರ್ ಸಹಾಯವಾಣಿ", "ಸೈಬರ್ ಪೊಲೀಸ್",
            "സൈബർ സുരക്ഷ", "സുരക്ഷാ അറിയിപ്പ്", "സൈബർ ഹെൽപ്പ്‌ലൈൻ", "സൈബർ പോലീസ്",
            "ਸਾਈਬਰ ਸੁਰੱਖਿਆ", "ਸੁਰੱਖਿਆ ਨੋਟਿਸ", "ਸਾਈਬਰ ਹੈਲਪਲਾਈਨ", "ਸਾਈਬਰ ਪੁਲਿਸ",
            "cyber helpline", "cyber helpline 1930", "helpline 1930", "हेल्पलाइन 1930"
        )
        if (globalIndicators.any { lower.contains(it) }) return true

        if (span != null) {
            val windowStart = (span.start - 60).coerceAtLeast(0)
            val windowEnd = (span.end + 60).coerceAtMost(text.length)
            val surrounding = text.substring(windowStart, windowEnd).lowercase()

            val localIndicators = listOf(
                "warning", "advisory", "fake", "fake hai", "satark", "savdhan",
                "अलर्ट", "फर्जी", "बचें", "धोखाधड़ी", "dhokhadhadi"
            )
            if (localIndicators.any { surrounding.contains(it) }) return true
        }

        return false
    }

    private fun isAwarenessOrAdvisory(text: String, match: LexiconMatch): Boolean {
        return isAwarenessOrAdvisory(text, TextSpan(match.start, match.end))
    }

    private fun isDirectedThreat(textNorm: String, textDeob: String, match: LexiconMatch): Boolean {
        val isAwareness = isAwarenessOrAdvisory(textNorm) || isAwarenessOrAdvisory(textDeob)
        if (!isAwareness) return true

        // In awareness context, check if the threat is aimed at the reader
        val texts = listOf(textNorm, textDeob)
        for (text in texts) {
            val len = text.length
            if (len == 0) continue

            val start = match.start.coerceIn(0, len)
            val end = match.end.coerceIn(start, len)

            // Delimit clause boundaries
            val delimiters = charArrayOf('.', '!', '?', ';', ':', '\n', '|', '…')
            var clauseStart = 0
            for (i in start - 1 downTo 0) {
                if (text[i] in delimiters) {
                    clauseStart = i + 1
                    break
                }
            }
            var clauseEnd = len
            for (i in end until len) {
                if (text[i] in delimiters) {
                    clauseEnd = i
                    break
                }
            }

            val clause = text.substring(clauseStart, clauseEnd).trim().lowercase()
            val prefixWindowStart = (start - 60).coerceAtLeast(0)
            val prefixWindow = text.substring(prefixWindowStart, start).lowercase()

            val thirdPersonPrefixRegex = Regex(
                """\b(warns?\s+against|warning\s+against|advis(?:es?|ory)\s+against|calling\s+claiming|calls?\s+claiming|messages?\s+claiming|notices?\s+claiming|claiming\s+to|claiming|claims|claimed|fraudsters?\s+are|scammers?\s+are|thagon?\s+(?:dwara|se)|dar\s+dikhakar|dhamki\s+dekar|bolkar|ke\s+naam\s+par|दावा|धमकी देकर|डर दिखाकर|ठग|धोखेबाज)\b"""
            )
            val isThirdPersonReporting = thirdPersonPrefixRegex.containsMatchIn(prefixWindow) ||
                    thirdPersonPrefixRegex.containsMatchIn(clause)

            val secondPersonRegex = Regex(
                """\b(you|your|yours|yourself|u|ur|aap|aapka|aapke|aapki|aapko|tum|tumhara|tumhari|tumhare|tumhe|tera|teri|tere|tujhe)\b|""" +
                """(आप|आपका|आपके|आपकी|आपको|तुम|तुम्हारा|तुम्हारे|तुम्हारी|तुम्हें|तेरा|तेरी|तेरे|तुझे)"""
            )
            val hasSecondPerson = secondPersonRegex.containsMatchIn(clause)

            val imperativeRegex = Regex(
                """\b(call|pay|transfer|send|dial|contact|deposit|settle|karein|karo|kijiye|bhejo|bhejein)\b|""" +
                """(करें|करो|कीजिए|भेजें|भेजो|भुगतान|कॉल|संपर्क)"""
            )
            val hasImperative = imperativeRegex.findAll(clause).any { m ->
                val sub = clause.substring(0, m.range.first)
                !sub.endsWith("not ") && !sub.endsWith("never ") && !sub.endsWith("don't ") &&
                !sub.endsWith("mat ") && !sub.endsWith("na ") && !sub.endsWith("न ") && !sub.endsWith("मत ")
            }

            if (isThirdPersonReporting && !hasSecondPerson) {
                return false
            }

            if (hasSecondPerson || hasImperative) {
                return true
            }
        }

        return false
    }

    private fun hasImpersonationClaim(
        textNorm: String,
        textDeob: String,
        brand: ExtractedBrand,
        matches: Map<String, LexiconMatch>
    ): Boolean {
        // 1. Generic greeting or mass address (P14)
        if (matches.containsKey("generic_mass_greeting")) return true

        val texts = listOf(textNorm.lowercase(), textDeob.lowercase())
        val brandNames = listOf(brand.rawText.lowercase(), brand.brandName.lowercase(), brand.brandId.lowercase()).distinct()

        val impersonationPrefixes = listOf(
            "from ", "this is ", "speaking from ", "calling from ", "we are ",
            "on behalf of ", "behalf of ", "official ", "this is your ", "main ", "hum "
        )
        val impersonationSuffixes = listOf(
            " official", " team", " support", " care", " customer care", " helpline",
            " helpdesk", " desk", " department", " branch", " notice", " alert",
            " representative", " officer", " ki taraf se", " se bol", " se bol raha hoon",
            " se bol rahe hain", " shakha",
            " adhikari", " vibhag", " की तरफ से", " से बोल", " हेल्पलाइन", " शाखा",
            " अधिकारी", " विभाग", " portal", " kyc", " verification", " account",
            " account blocked", " account locked", " debit card", " account holder",
            " cardholder", " customer", " user",
            " खाताधारक", " खाता", " ग्राहक", " उपभोक्ता", " धारक", ":", " :"
        )

        for (t in texts) {
            for (bLower in brandNames) {
                for (p in impersonationPrefixes) {
                    if (t.contains(p + bLower)) return true
                }
                for (s in impersonationSuffixes) {
                    if (t.contains(bLower + s)) return true
                }
            }

            val authorityPretexts = listOf(
                "dear customer", "dear user", "dear sir", "dear madam", "dear cardholder",
                "attention customer", "priya grahak", "प्रिय ग्राहक", "प्रिय उपभोक्ता",
                "customer care", "support team", "head office", "branch manager",
                "ki taraf se", "se bol rahe", "se bol raha", "की तरफ से",
                "this is your bank", "main bank se bol raha hoon", "main bank se bol raha hu",
                "hum bank se bol rahe hain"
            )
            if (authorityPretexts.any { t.contains(it) }) return true
        }

        return false
    }

    private fun mapMatchToOriginalSpan(match: LexiconMatch, normalized: NormalizedText): TextSpan {
        val map = normalized.indexMap
        if (map.isEmpty()) return TextSpan(0, 0)
        val s = if (match.start in map.indices) map[match.start] else 0
        val e = if (match.end - 1 in map.indices) map[match.end - 1] + 1 else s + (match.end - match.start)
        return TextSpan(s, maxOf(s, e))
    }

    private fun matchesDltBrand(dltHeaderBrand: String?, brand: ExtractedBrand): Boolean {
        if (dltHeaderBrand.isNullOrEmpty()) return false
        val dltUpper = dltHeaderBrand.uppercase()
        val bIdUpper = brand.brandId.uppercase()
        val bNameUpper = brand.brandName.uppercase()

        // Police / law enforcement headers must be strictly verified against allowlist
        if (brand.brandKind == BrandKind.LAW_ENFORCEMENT) {
            return policeDltHeaders.contains(dltUpper)
        }

        if (dltUpper.contains(bIdUpper) || bIdUpper.contains(dltUpper)) return true
        if (dltUpper.contains(bNameUpper) || bNameUpper.contains(dltUpper)) return true

        return when (brand.brandId.lowercase()) {
            "sbi" -> dltUpper.contains("SBI")
            "hdfc" -> dltUpper.contains("HDFC")
            "icici" -> dltUpper.contains("ICICI")
            "pnb" -> dltUpper.contains("PNB")
            "axis" -> dltUpper.contains("AXIS")
            "kotak" -> dltUpper.contains("KOTAK")
            "bob" -> dltUpper.contains("BOB") || dltUpper.contains("BARODA")
            "jio" -> dltUpper.contains("JIO")
            "airtel" -> dltUpper.contains("AIRTEL") || dltUpper.contains("AIRTL")
            "indiapost" -> dltUpper.contains("POST") || dltUpper.contains("IPPB") || dltUpper.contains("DOP") || dltUpper.contains("INDPOST")
            "incometax" -> dltUpper.contains("ITD") || dltUpper.contains("INCTAX") || dltUpper.contains("INCOM")
            "uidai" -> dltUpper.contains("UIDAI") || dltUpper.contains("AADHAAR")
            "epfo" -> dltUpper.contains("EPFO")
            "parivahan" -> dltUpper.contains("PARIV") || dltUpper.contains("VAHAN") || dltUpper.contains("ECHAL") || dltUpper.contains("SARAT")
            "electricity" -> dltUpper.contains("BSES") || dltUpper.contains("MSEDCL") || dltUpper.contains("UPPCL") || dltUpper.contains("TATAPW") || dltUpper.contains("BESCOM") || dltUpper.contains("POWER") || dltUpper.contains("BIJLI") || dltUpper.contains("ELEC")
            "police" -> policeDltHeaders.contains(dltUpper)
            "wbsedcl" -> dltUpper.contains("WBSEDCL")
            "msedcl" -> dltUpper.contains("MSEDCL")
            "tpcodl" -> dltUpper.contains("TPCODL")
            "apcpdcl" -> dltUpper.contains("APCPDC")
            "tgspdcl" -> dltUpper.contains("TGSPDC") || dltUpper.contains("TSSPDC")
            "tangedco" -> dltUpper.contains("TNEB") || dltUpper.contains("TANGEDCO")
            "phonepe" -> dltUpper.contains("PHNPE") || dltUpper.contains("PHONEPE")
            "paytm" -> dltUpper.contains("PAYTM")
            "gpay" -> dltUpper.contains("GPAY") || dltUpper.contains("GOOGLE")
            else -> false
        }
    }

    fun hasCallbackAsk(normalized: NormalizedText, extracted: ExtractionResult): Boolean {
        if (extracted.phones.isEmpty()) return false
        val text = normalized.normalizedText.lowercase()
        val deob = normalized.deobfuscatedText.lowercase()
        val callbackRegex = Regex(
            """\b(call|dial|contact|whatsapp|whats\s*app|msg|message|ring|phone)\b|""" +
            """(कॉल|डायल|संपर्क|व्हाट्सएप|मैसेज|फोन)"""
        )
        for (phone in extracted.phones) {
            val pStart = phone.span.start
            val pEnd = phone.span.end
            for (src in listOf(text, deob)) {
                val windowStart = (pStart - 60).coerceAtLeast(0)
                val windowEnd = (pEnd + 60).coerceAtMost(src.length)
                if (windowStart >= windowEnd) continue
                val window = src.substring(windowStart, windowEnd)
                for (match in callbackRegex.findAll(window)) {
                    val sub = window.substring(0, match.range.first)
                    val isNegated = sub.endsWith("do not ") || sub.endsWith("dont ") || sub.endsWith("don't ") ||
                            sub.endsWith("never ") || sub.endsWith("mat ") || sub.endsWith("na ") ||
                            sub.endsWith("मत ") || sub.endsWith("न ")
                    if (!isNegated) {
                        return true
                    }
                }
            }
        }
        return false
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


