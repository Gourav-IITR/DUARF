// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

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

    @Test
    fun `adversarial english OTP evasion fires A01 when do not share precedes request to send to me`() {
        val msg = createMessage("Don't share with anyone, send the OTP to me immediately to prevent block.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial english OTP evasion fires A01 when request to send to me precedes do not share`() {
        val msg = createMessage("Send the OTP to me right now. Do not tell anyone else.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial hindi OTP evasion fires A01 when kisi ko mat batana precedes otp mujhe bhejo`() {
        val msg = createMessage("किसी को मत बताना, ओटीपी मुझे भेजो खाता चालू रखने के लिए।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial hindi OTP evasion fires A01 when otp mujhe bhejo precedes kisi ko mat batana`() {
        val msg = createMessage("ओटीपी मुझे भेजो तुरंत। किसी को मत बताना यह गोपनीय है।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial hinglish OTP evasion fires A01 when kisi ko mat batana precedes otp mujhe bhejo`() {
        val msg = createMessage("Kisi ko mat batana, OTP mujhe bhejo account reactivate karne ke liye.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial hinglish OTP evasion fires A01 when otp mujhe bhejo precedes kisi ko mat batana`() {
        val msg = createMessage("OTP mujhe bhejo jaldi se, kisi ko mat batana.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
    }
}

