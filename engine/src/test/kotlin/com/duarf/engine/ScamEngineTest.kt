package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.signal.FiredSignal
import com.duarf.engine.signal.ScoreFusion
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ScamEngineTest {

    private lateinit var engine: ScamEngine

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(rootDir))
    }

    private fun createMessage(
        text: String,
        senderKind: SenderKind = SenderKind.NUMBER_ONLY,
        senderCountryCode: String? = "+91",
        isGroup: Boolean = false,
        senderDisplay: String? = "+919876543210"
    ): IncomingMessage {
        return IncomingMessage(
            fingerprint = "test-fp",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-1",
            senderDisplay = senderDisplay,
            senderKind = senderKind,
            senderCountryCode = senderCountryCode,
            isGroup = isGroup,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    @Test
    fun `one weak signal stays NONE (§10)`() {
        // Pure text with no asks, just from a number-only sender (S01 only, weight 0.10)
        val msg = createMessage("Hello, how are you?")
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
        assertThat(verdict.score).isLessThan(0.45)
    }

    @Test
    fun `L01 alone from a named sender is CAUTION (§10)`() {
        // APK file from named contact (L01 weight 0.55, no S01)
        val msg = createMessage(
            text = "Here is the new update.apk file",
            senderKind = SenderKind.NAMED,
            senderDisplay = "Alice"
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.CAUTION)
        assertThat(verdict.score).isAtLeast(0.45)
        assertThat(verdict.score).isLessThan(0.72)
        assertThat(verdict.category).isEqualTo(ScamCategory.MALICIOUS_APK)
    }

    @Test
    fun `L01 from a number-only sender triggers C02 and is DANGER (§10)`() {
        // APK from number-only sender (L01 + S01 -> C02 floor 0.85)
        val msg = createMessage(
            text = "Install update.apk immediately",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.score).isAtLeast(0.72)
        assertThat(verdict.category).isEqualTo(ScamCategory.MALICIOUS_APK)
        assertThat(verdict.reasons).isNotEmpty()
    }

    @Test
    fun `genuine OTP delivery is NONE (§10)`() {
        // Genuine bank OTP with "do not share", no URL, no asks (B01 dampener applied)
        val msg = createMessage(
            text = "Your OTP for transaction of Rs. 1500 is 492018. Do not share with anyone.",
            senderKind = SenderKind.NAMED,
            senderDisplay = "HDFCBK"
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `model at 0_99 with no concrete signal is CAUTION (Invariant 6)`() {
        // Test ScoreFusion directly with no concrete signals and high model probability
        val fusion = ScoreFusion.fuse(
            signals = listOf(
                FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)
            ),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.99,
            sensitivity = Sensitivity.BALANCED
        )

        // Clamped below Danger threshold 0.72 -> CAUTION
        assertThat(fusion.level).isEqualTo(AlertLevel.CAUTION)
        assertThat(fusion.score).isLessThan(0.72)
        assertThat(fusion.score).isAtLeast(0.45)
    }

    @Test
    fun `digital arrest scam triggers C04 DANGER`() {
        val msg = createMessage(
            text = "This is CBI officer. Digital arrest warrant issued against you for narcotics parcel. Do not disconnect video call."
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.AUTHORITY_DIGITAL_ARREST)
        assertThat(verdict.reasons.any { it.signalId == "P03" }).isTrue()
    }

    @Test
    fun `electricity disconnect scam triggers C06 DANGER`() {
        val msg = createMessage(
            text = "Dear customer, your electricity will be disconnected tonight 9:30 pm due to unpaid bill. Click link to update: http://power-bill.xyz"
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.UTILITY_DISCONNECT)
    }

    @Test
    fun `UPI PIN to receive money triggers C05 DANGER`() {
        val msg = createMessage(
            text = "Rs 5000 cashback approved! Enter upi pin to receive money in your account now"
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.UPI_PAYMENT_FRAUD)
    }

    @Test
    fun `lookalike domain with bank brand and KYC threat triggers C07 DANGER`() {
        val msg = createMessage(
            text = "SBI Alert: Your account will be blocked within 24 hours. Update KYC at http://sbi-kyc-verify.xyz immediately."
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.PHISHING_BANK_KYC)
    }

    @Test
    fun `Hindi scam message in Devanagari is detected`() {
        val msg = createMessage(
            text = "प्रिय ग्राहक, आपका बिजली कनेक्शन काट दिया जाएगा। तुरंत बिल भरें।"
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
        assertThat(verdict.category).isEqualTo(ScamCategory.UTILITY_DISCONNECT)
    }

    @Test
    fun `Hinglish scam message in Roman script is detected`() {
        val msg = createMessage(
            text = "Dear user, aapki bijli kat jayegi aaj raat tak. turant payment karein."
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
        assertThat(verdict.category).isEqualTo(ScamCategory.UTILITY_DISCONNECT)
    }

    @Test
    fun `engine never throws on extreme inputs (§16_1)`() {
        // Empty
        engine.analyze(createMessage(""))

        // Whitespace only
        engine.analyze(createMessage("     \n\t   "))

        // 100,000 characters
        engine.analyze(createMessage("a".repeat(100_000)))

        // Emoji only
        engine.analyze(createMessage("🔥💀🚨💸🎁🚗⚡️📞".repeat(100)))

        // Malformed surrogates
        engine.analyze(createMessage("Hello \uD800 world \uDFFF test"))
    }
}
