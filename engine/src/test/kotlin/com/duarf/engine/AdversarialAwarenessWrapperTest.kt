// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

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
            text = "Security advisory forward: Cyber police warns citizens against fake lottery claims and suspicious links. Please inform your parents.",
            sender = "Mom",
            senderKind = SenderKind.NAMED
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `pure scam awareness message 4 from unknown number results in NONE`() {
        val msg = createMessage(
            text = "Beware! Fraudsters are sending fake e-challan APK files on WhatsApp. Never install apps sent in chats.",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
        assertThat(verdict.score).isLessThan(0.35)
    }

    @Test
    fun `real scam wrapped in awareness text with OTP request results in DANGER`() {
        val msg = createMessage(
            text = "Beware of fraud! Send me the OTP to verify",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "A01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.score).isAtLeast(0.72)
    }

    @Test
    fun `bank never share OTP notice from unknown number results in NONE`() {
        val msg = createMessage(
            text = "Security Notice from SBI: Bank will never ask for your OTP, ATM PIN or netbanking password. Beware of fake calls and never share your security codes.",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `Hindi police advisory forward from unknown number results in NONE`() {
        val msg = createMessage(
            text = "साइबर पुलिस चेतावनी: व्हाट्सएप पर भेजे जा रहे फर्जी ई-चालान और एपीके फाइलों से सावधान रहें। किसी भी चैट में भेजी गई ऐप को इंस्टॉल न करें।",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `Hinglish family group warning forward from unknown number results in NONE`() {
        val msg = createMessage(
            text = "Forwarded for awareness: Dosto YouTube videos like karke daily paise kamane wale task scam se bachein. Telegram channel join mat karna.",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `adversarial scam wrapped in awareness with APK download link results in DANGER`() {
        val msg = createMessage(
            text = "Police Advisory: Beware of fraudsters sending fake apps. Please download the verified safety update from http://police-safety-portal.xyz/secure.apk to scan your phone.",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "L01" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `awareness wrapped scam with soft utility disconnect threat fires P04 and alerts CAUTION`() {
        val msg = createMessage(
            text = "Beware of fake callers. This is the real electricity office: your power will be cut tonight, call 98765xxxxx",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "P04" }).isTrue()
        assertThat(verdict.level).isNotEqualTo(AlertLevel.NONE)
        assertThat(verdict.score).isAtLeast(0.45)
    }

    @Test
    fun `awareness wrapped scam with digital arrest threat and payment ask results in DANGER`() {
        val msg = createMessage(
            text = "Public warning: Fraudsters are impersonating officers. You are under digital arrest by CBI, pay fine immediately to clear name.",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "P03" }).isTrue()
        assertThat(verdict.reasons.any { it.signalId == "A03" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `tst-ben-warning-01 style third-person advisory results in NONE`() {
        val msg = createMessage(
            text = "Security advisory forward: Cyber police warns against answering video calls claiming digital arrest or drug parcel charges. Please inform your parents. Helpline: 1800-2462-2462",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "P03" }).isFalse()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `directed threat with digital arrest and pay fine results in DANGER`() {
        val msg = createMessage(
            text = "Beware… you are under digital arrest, pay fine",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "P03" }).isTrue()
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `directed threat with power cut and call directive results in CAUTION or above`() {
        val msg = createMessage(
            text = "Beware of fake callers… your power will be cut tonight, call…",
            sender = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY
        )
        val verdict = engine.analyze(msg)

        assertThat(verdict.reasons.any { it.signalId == "P04" }).isTrue()
        assertThat(verdict.level).isIn(listOf(AlertLevel.CAUTION, AlertLevel.DANGER))
    }
}

