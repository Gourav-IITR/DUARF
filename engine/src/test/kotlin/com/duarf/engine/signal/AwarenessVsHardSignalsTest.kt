// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.signal

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.LoadedPacks
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class AwarenessVsHardSignalsTest {

    private lateinit var loadedPacks: LoadedPacks
    private lateinit var engine: DefaultScamEngine
    private lateinit var entityExtractor: EntityExtractor

    @Before
    fun setUp() {
        val packsDir = listOf(File("packs"), File("../packs"), File("../../packs"))
            .firstOrNull { it.exists() && File(it, "rules.json").exists() }
            ?: File("packs")
        loadedPacks = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(loadedPacks)
        val psl = if (loadedPacks.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(loadedPacks.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, loadedPacks.brands, loadedPacks.upiHandles)
    }

    private fun checkAwarenessPlusOtpAsk(lang: String, text: String) {
        val msg = IncomingMessage(
            fingerprint = "msg-awareness-otp-$lang",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-$lang",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)

        val (signals, dampeners) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        val signalIds = signals.map { it.signalId }.toSet()
        val dampenerIds = dampeners.map { it.signalId }.toSet()

        // 1. A01 (asks_otp_pin_cvv) MUST fire
        assertThat(signalIds).contains("A01")

        // 2. B05 (awareness_or_advisory_context) MUST NOT apply
        assertThat(dampenerIds).doesNotContain("B05")

        // 3. Engine verdict level must be DANGER (hard signal + unknown sender)
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun testBengali_awarenessTextPlusOtpAsk_firesA01_blocksB05() {
        // Bengali: "সাইবার সুরক্ষা সতর্কবার্তা: সতর্ক থাকুন। যাচাইয়ের জন্য অবিলম্বে আমাকে ওটিপি দিন।"
        // (Cyber security advisory: Stay alert. For verification immediately give me OTP.)
        checkAwarenessPlusOtpAsk("bn", "সাইবার সুরক্ষা সতর্কবার্তা: সতর্ক থাকুন। যাচাইয়ের জন্য অবিলম্বে আমাকে ওটিপি দিন।")
    }

    @Test
    fun testMarathi_awarenessTextPlusOtpAsk_firesA01_blocksB05() {
        // Marathi: "सायबर सुरक्षा सल्ला: सावध राहा. पडताळणी पूर्ण करण्यासाठी मला ओटीपी पाठवा."
        // (Cyber security advisory: Stay alert. Send OTP to me to complete verification.)
        checkAwarenessPlusOtpAsk("mr", "सायबर सुरक्षा सल्ला: सावध राहा. पडताळणी पूर्ण करण्यासाठी मला ओटीपी पाठवा.")
    }

    @Test
    fun testTelugu_awarenessTextPlusOtpAsk_firesA01_blocksB05() {
        // Telugu: "సైబర్ భద్రతా హెచ్చరిక: జాగ్రత్తగా ఉండండి. ధృవీకరణ కోసం నాకు ఓటీపీ పంపండి."
        // (Cyber security alert: Stay careful. Send me OTP for verification.)
        checkAwarenessPlusOtpAsk("te", "సైబర్ భద్రతా హెచ్చరిక: జాగ్రత్తగా ఉండండి. ధృవీకరణ కోసం నాకు ఓటీపీ పంపండి.")
    }

    @Test
    fun testTamil_awarenessTextPlusOtpAsk_firesA01_blocksB05() {
        // Tamil: "சைபர் பாதுகாப்பு எச்சரிக்கை: விழிப்புடன் இருங்கள். சரிபார்ப்பை முடிக்க எனக்கு ஓடிபி அனுப்புங்கள்."
        // (Cyber security alert: Stay vigilant. Send me OTP to complete verification.)
        checkAwarenessPlusOtpAsk("ta", "சைபர் பாதுகாப்பு எச்சரிக்கை: விழிப்புடன் இருங்கள். சரிபார்ப்பை முடிக்க எனக்கு ஓடிபி அனுப்புங்கள்.")
    }

    @Test
    fun testOdia_awarenessTextPlusOtpAsk_firesA01_blocksB05() {
        // Odia: "ସାଇବର ସୁରକ୍ଷା ଚେତାବନୀ: ସତର୍କ ରୁହନ୍ତୁ। ଯାଞ୍ଚ ପାଇଁ ମୋତେ ଓଟିପି ପଠାନ୍ତୁ।"
        // (Cyber security warning: Stay alert. Send me OTP for verification.)
        checkAwarenessPlusOtpAsk("or", "ସାଇବର ସୁରକ୍ଷା ଚେତାବନୀ: ସତର୍କ ରୁହନ୍ତୁ। ଯାଞ୍ଚ ପାଇଁ ମୋତେ ଓଟିପି ପଠାନ୍ତୁ।" )
    }

    @Test
    fun testAwarenessPlusA03Ask_firesA03_blocksB05_doesNotSuppressS04AndP10() {
        val msg = IncomingMessage(
            fingerprint = "msg-awareness-a03",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GENERIC,
            conversationKey = "conv-sbi-a03",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.PERSONAL_NUMBER,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Cyber awareness advisory from SBI: Pay Rs 500 processing fee to secure your account immediately.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)
        val (signals, dampeners) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        val signalIds = signals.map { it.signalId }.toSet()
        val dampenerIds = dampeners.map { it.signalId }.toSet()

        assertThat(signalIds).contains("A03")
        assertThat(signalIds).contains("S04")
        assertThat(signalIds).contains("P10")
        assertThat(dampenerIds).doesNotContain("B05")
    }

    @Test
    fun testAwarenessPlusA07Ask_firesA07_blocksB05_doesNotSuppressS04AndP10() {
        val msg = IncomingMessage(
            fingerprint = "msg-awareness-a07",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GENERIC,
            conversationKey = "conv-sbi-a07",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.PERSONAL_NUMBER,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Cyber security awareness: Dear customer, your SBI account is locked. Click here to update your details.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)
        val (signals, dampeners) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        val signalIds = signals.map { it.signalId }.toSet()
        val dampenerIds = dampeners.map { it.signalId }.toSet()

        assertThat(signalIds).contains("A07")
        assertThat(signalIds).contains("S04")
        assertThat(signalIds).contains("P10")
        assertThat(dampenerIds).doesNotContain("B05")
    }

    @Test
    fun testPureAwareness_suppressesS04AndP10_appliesB05() {
        val msg = IncomingMessage(
            fingerprint = "msg-awareness-pure",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GENERIC,
            conversationKey = "conv-sbi-pure",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.PERSONAL_NUMBER,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Cyber security advisory: State Bank of India reminds you to never share your password with anyone. Stay safe.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)
        val (signals, dampeners) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        val signalIds = signals.map { it.signalId }.toSet()
        val dampenerIds = dampeners.map { it.signalId }.toSet()

        // Pure awareness without asks, links, or threats suppresses S04 and P10 and applies B05
        assertThat(signalIds).doesNotContain("S04")
        assertThat(signalIds).doesNotContain("P10")
        assertThat(dampenerIds).contains("B05")

        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }
}
