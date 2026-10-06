package com.duarf.engine

import com.duarf.engine.extract.DltHeaderParser
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class SmsScamDetectionTest {

    private lateinit var engine: ScamEngine

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(rootDir))
    }

    private fun createSmsMessage(
        text: String,
        sender: String,
        senderKind: SenderKind? = null
    ): IncomingMessage {
        val parsed = DltHeaderParser.parse(sender)
        val sKind = senderKind ?: parsed.senderKind
        return IncomingMessage(
            fingerprint = "sms-fp-${System.nanoTime()}",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GOOGLE_MESSAGES,
            conversationKey = "sms-conv",
            senderDisplay = sender,
            senderKind = sKind,
            senderCountryCode = parsed.countryCode ?: "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis(),
            dltHeaderPrefix = parsed.dltPrefix,
            dltHeaderBrand = parsed.dltBrand,
            dltHeaderSuffix = parsed.dltSuffix
        )
    }

    // --- 1. DltHeaderParser Tests ---

    @Test
    fun `parse standard TRAI prefixed DLT headers with suffixes`() {
        val parsedHdfc = DltHeaderParser.parse("AX-HDFCBK-T")
        assertThat(parsedHdfc.senderKind).isEqualTo(SenderKind.DLT_HEADER)
        assertThat(parsedHdfc.dltPrefix).isEqualTo("AX")
        assertThat(parsedHdfc.dltBrand).isEqualTo("HDFCBK")
        assertThat(parsedHdfc.dltSuffix).isEqualTo("T")

        val parsedSwiggy = DltHeaderParser.parse("BZ-SWIGGY-S")
        assertThat(parsedSwiggy.senderKind).isEqualTo(SenderKind.DLT_HEADER)
        assertThat(parsedSwiggy.dltPrefix).isEqualTo("BZ")
        assertThat(parsedSwiggy.dltBrand).isEqualTo("SWIGGY")
        assertThat(parsedSwiggy.dltSuffix).isEqualTo("S")

        val parsedPromo = DltHeaderParser.parse("AX-MYNTRA-P")
        assertThat(parsedPromo.senderKind).isEqualTo(SenderKind.DLT_HEADER)
        assertThat(parsedPromo.dltPrefix).isEqualTo("AX")
        assertThat(parsedPromo.dltBrand).isEqualTo("MYNTRA")
        assertThat(parsedPromo.dltSuffix).isEqualTo("P")

        val parsedGov = DltHeaderParser.parse("JK-EPFOGV-G")
        assertThat(parsedGov.senderKind).isEqualTo(SenderKind.DLT_HEADER)
        assertThat(parsedGov.dltPrefix).isEqualTo("JK")
        assertThat(parsedGov.dltBrand).isEqualTo("EPFOGV")
        assertThat(parsedGov.dltSuffix).isEqualTo("G")
    }

    @Test
    fun `parse bare headers and short codes`() {
        val bare = DltHeaderParser.parse("SBIBNK-T")
        assertThat(bare.senderKind).isEqualTo(SenderKind.DLT_HEADER)
        assertThat(bare.dltPrefix).isNull()
        assertThat(bare.dltBrand).isEqualTo("SBIBNK")
        assertThat(bare.dltSuffix).isEqualTo("T")

        val shortCode = DltHeaderParser.parse("56767")
        assertThat(shortCode.senderKind).isEqualTo(SenderKind.SHORT_CODE)
    }

    @Test
    fun `parse Indian personal mobile numbers`() {
        val formats = listOf(
            "+919876543210",
            "+91 98765 43210",
            "+91-9876543210",
            "9876543210",
            "09876543210"
        )
        for (f in formats) {
            val parsed = DltHeaderParser.parse(f)
            assertThat(parsed.senderKind).isEqualTo(SenderKind.PERSONAL_NUMBER)
            assertThat(parsed.countryCode).isEqualTo("+91")
        }
    }

    // --- 2. S04: Institution Claim from Personal Number ---

    @Test
    fun `S04 fires when bank is claimed from personal number on SMS`() {
        val msg = createSmsMessage(
            text = "Dear customer, your SBI account is suspended. Contact our branch manager immediately.",
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        val s04 = verdict.reasons.find { it.signalId == "S04" }
        assertThat(s04).isNotNull()
    }

    @Test
    fun `S04 does not fire on WhatsApp messages`() {
        val waMsg = IncomingMessage(
            fingerprint = "wa-test",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "wa-conv",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Dear customer, your SBI account is suspended.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val verdict = engine.analyze(waMsg)
        val s04 = verdict.reasons.find { it.signalId == "S04" }
        assertThat(s04).isNull()
    }

    @Test
    fun `C11 combo escalates S04 plus link or ask to DANGER`() {
        // S04 (claim SBI from personal number) + L05 (shortener) -> C11 combo (Floor 0.82) -> DANGER
        val msg = createSmsMessage(
            text = "SBI Bank alert: Update your KYC details at https://tinyurl.com/sbi-kyc or account will be suspended.",
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.score).isAtLeast(0.82)
    }

    // --- 3. S05: Header Claim Mismatch ---

    @Test
    fun `S05 fires when DLT header brand does not match claimed institutional brand`() {
        // Sender header is ShopClues (SHPCLU), but message claims State Bank of India
        val msg = createSmsMessage(
            text = "State Bank of India: Your account has been credited with Rs 50,000. Verify KYC at http://sbi-reward.xyz to claim.",
            sender = "AX-SHPCLU-T"
        )
        val verdict = engine.analyze(msg)
        val s05 = verdict.reasons.find { it.signalId == "S05" }
        assertThat(s05).isNotNull()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `S05 fires when promotional -P header asks for sensitive credentials`() {
        val msg = createSmsMessage(
            text = "Urgent notice: Share your OTP immediately with customer support to claim your cash reward.",
            sender = "AX-XYZOFR-P"
        )
        val verdict = engine.analyze(msg)
        val s05 = verdict.reasons.find { it.signalId == "S05" }
        assertThat(s05).isNotNull()
    }

    // --- 4. B06: Verified Header Consistent Dampener ---

    @Test
    fun `B06 fires and dampens legitimate bank SMS from verified header to NONE`() {
        val msg = createSmsMessage(
            text = "Dear Customer, INR 5,000 debited from your HDFC Bank A/c ending 1234 on 06-10-2026. Info: UPI/Ref 123456. Not you? Call 180020261234.",
            sender = "AX-HDFCBK-T"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
        assertThat(verdict.score).isLessThan(0.20)
    }

    @Test
    fun `B06 does not suppress hard signals`() {
        // Even from a verified -T header, sending an APK link (hard signal L01) must stay DANGER
        val msg = createSmsMessage(
            text = "HDFC Bank: Download our new update from http://hdfcbank.com/update.apk to continue banking.",
            sender = "AX-HDFCBK-T"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    // --- 5. SMS Model Influence Cap & Invariant 6 ---

    @Test
    fun `SMS with low rule score cannot reach DANGER via ML model alone`() {
        // Create an ambiguous SMS message with no hard or combo signals
        val msg = createSmsMessage(
            text = "Congratulations, you have been selected for a special interview tomorrow morning. Please be available.",
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        // Without hard signal, SMS cannot reach DANGER
        assertThat(verdict.level).isNotEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `S04 alone without combo floor or hard signal is capped at CAUTION satisfying Invariant 6`() {
        val msg = createSmsMessage(
            text = "Dear customer, Electricity Board welcomes you to our service.",
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.reasons.any { it.signalId == "S04" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.CAUTION)
        assertThat(verdict.score).isLessThan(0.72)
    }

    @Test
    fun `C12 combo escalates S04 plus threat or urgency to DANGER with floor 0_82`() {
        // S04 + P01 (urgency deadline) -> C12 (Floor 0.82) -> DANGER
        val msg = createSmsMessage(
            text = "Dear customer, Electricity Board urgent deadline expires tonight at 9 PM. Contact immediately.",
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.score).isAtLeast(0.82)
        assertThat(verdict.reasons.any { it.signalId == "S04" }).isTrue()
        assertThat(verdict.reasons.any { it.signalId == "P01" }).isTrue()
    }

    @Test
    fun `Case B electricity disconnect scam reaches DANGER with P04 P01 S04 and clean highlights`() {
        val text = "Dear consumer, Electricity Board will disconnect your power supply tonight at 9:30 PM due to unpaid bill. Call 9876543210 to pay immediately."
        val msg = createSmsMessage(
            text = text,
            sender = "+919876543210"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.UTILITY_DISCONNECT)

        val reasonIds = verdict.reasons.map { it.signalId }
        assertThat(reasonIds).contains("P04")
        assertThat(reasonIds).contains("P01")
        assertThat(reasonIds).contains("S04")

        // Highlights must not contain stopwords like "will" or short tokens like "to"
        val highlightedWords = verdict.highlights.map { text.substring(it.start, it.end) }
        assertThat(highlightedWords).doesNotContain("will")
        assertThat(highlightedWords).doesNotContain("to")
        assertThat(highlightedWords).contains("Electricity Board")
        assertThat(highlightedWords).contains("will disconnect your power supply")
    }
}
