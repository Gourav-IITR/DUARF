package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class NegativeLexiconOtpTest {

    private lateinit var engine: DefaultScamEngine

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))
    }

    private fun createMessage(text: String, sender: String = "SBI-ALERT"): IncomingMessage {
        return IncomingMessage(
            fingerprint = "fp_otp_test",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv_otp_test",
            senderDisplay = sender,
            senderKind = SenderKind.NAMED,
            senderCountryCode = null,
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    @Test
    fun `genuine english OTP delivery does not fire A01 and results in NONE`() {
        val msg = createMessage("Your SBI OTP is 492019 for online transaction. Do not share your OTP with anyone.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.none { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `genuine english OTP with never share warning does not fire A01`() {
        val msg = createMessage("Never share your OTP with anyone. Your HDFC login verification code is 882194.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.none { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `scam english ask for OTP fires A01 and alerts`() {
        val msg = createMessage("Dear customer, please share your OTP to prevent your account from being blocked.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `genuine hindi OTP delivery does not fire A01 and results in NONE`() {
        val msg = createMessage("आपका एसबीआई ओटीपी 382910 है। यह ओटीपी किसी के साथ साझा न करें।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.none { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `scam hindi ask for OTP fires A01 and alerts`() {
        val msg = createMessage("अपना ओटीपी बताएं ताकि आपका बैंक खाता सक्रिय रह सके।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `genuine hinglish OTP delivery does not fire A01 and results in NONE`() {
        val msg = createMessage("Aapka ICICI OTP hai 554192. Yeh OTP kisi ke sath share na karein.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.none { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `scam hinglish ask for OTP fires A01 and alerts`() {
        val msg = createMessage("Apna OTP batao account verify karne ke liye abhi.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }
}
