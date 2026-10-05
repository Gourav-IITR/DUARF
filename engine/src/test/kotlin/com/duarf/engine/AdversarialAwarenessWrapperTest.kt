package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class AdversarialAwarenessWrapperTest {

    private lateinit var engine: DefaultScamEngine

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))
    }

    private fun createMessage(text: String, sender: String = "+919876543210", senderKind: SenderKind = SenderKind.NUMBER_ONLY): IncomingMessage {
        return IncomingMessage(
            fingerprint = "fp_adv_awareness",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv_adv_awareness",
            senderDisplay = sender,
            senderKind = senderKind,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    @Test
    fun `malicious APK wrapped in fraud warning still fires L01 and results in DANGER`() {
        val msg = createMessage("Security Advisory: Cyber police warns citizens against fake APKs. Please install our official scanner update.apk to verify your device.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "L01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `phishing link wrapped in security advisory still fires link signals and alerts`() {
        val msg = createMessage("Advisory from bank: Beware of phishing scams and fake callers. Immediately authenticate your identity at http://sbi-portal-verify.xyz/login to protect funds.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId.startsWith("L") }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `OTP ask wrapped in fraud awareness still fires A01 and alerts`() {
        val msg = createMessage("Beware of fraudulent transactions: An unauthorized payment of Rs 15000 was stopped. To cancel it, send the OTP to me immediately.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `UPI PIN collect scam wrapped in fraud warning still fires A04 and results in DANGER`() {
        val msg = createMessage("Fraud Alert: Beware of fake lottery claims. To reverse wrongful transaction, authorize with your secret UPI PIN to collect balance.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A04" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `genuine awareness advisory without hard scam signals results in NONE`() {
        val msg = createMessage(
            text = "Security advisory forward: Cyber police warns against answering video calls claiming digital arrest or drug parcel charges. Please inform your parents.",
            sender = "Mom",
            senderKind = SenderKind.NAMED
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }
}
