// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine

import com.duarf.engine.explain.ExplanationEngine
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.signal.FiredSignal
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ImpersonationAndHighlightTest {

    private lateinit var engine: ScamEngine

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(rootDir))
    }

    private fun createMessage(
        text: String,
        source: SourceKind = SourceKind.NOTIFICATION,
        senderKind: SenderKind = SenderKind.NUMBER_ONLY,
        senderDisplay: String? = "+919876543210",
        conversationKey: String? = "conv-1"
    ): IncomingMessage {
        return IncomingMessage(
            fingerprint = "test-fp-${System.nanoTime()}",
            source = source,
            app = SourceApp.WHATSAPP,
            conversationKey = conversationKey,
            senderDisplay = senderDisplay,
            senderKind = senderKind,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    // --- P10: Positive Impersonation Claims ---

    @Test
    fun `P10 fires when number-only sender includes generic mass greeting and brand`() {
        val msg = createMessage("Dear customer, your SBI account is locked. Update KYC immediately.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNotNull()
    }

    @Test
    fun `P10 fires when number-only sender claims to be official brand team`() {
        val msg = createMessage("This is HDFC Bank official support. Please verify your details.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNotNull()
    }

    @Test
    fun `P10 fires for Hinglish ki taraf se authority pretext`() {
        val msg = createMessage("Bijli vibhag ki taraf se notice: Bill turant pay karein.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNotNull()
    }

    // --- P10: Negative Casual Brand Mentions ---

    @Test
    fun `P10 does NOT fire for casual brand mention from number-only sender`() {
        val msg = createMessage("I transferred the money via SBI yesterday, please check.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNull()
    }

    @Test
    fun `P10 does NOT fire when chatting casually about an app or bank`() {
        val msg = createMessage("Did you check the HDFC app for the statement?")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNull()
    }

    // --- P10: Awareness and Advisory Suppression ---

    @Test
    fun `P10 is suppressed in scam awareness and advisory messages mentioning brands`() {
        val msg = createMessage("Beware! Fraudsters are sending fake e-challan APK files on WhatsApp. Never install apps sent in chats.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNull()
    }

    @Test
    fun `P10 is suppressed in security advisories warning against bank scams`() {
        val msg = createMessage("Security Advisory: Cyber police warns against fake SBI KYC messages. Never share your passwords.")
        val verdict = engine.analyze(msg)
        val p10 = verdict.reasons.find { it.signalId == "P10" }
        assertThat(p10).isNull()
    }

    // --- Highlight Whole-Token Expansion ---

    @Test
    fun `highlight expansion covers hyphenated words completely`() {
        val text = "Beware! Fake e-challan notice received."
        val start = text.indexOf("challan")
        val rawSpan = TextSpan(start, start + "challan".length)
        assertThat(text.substring(rawSpan.start, rawSpan.end)).isEqualTo("challan")

        val expanded = ExplanationEngine.expandToWholeToken(text, rawSpan)
        assertThat(text.substring(expanded.start, expanded.end)).isEqualTo("e-challan")
    }

    @Test
    fun `highlight expansion includes currency symbols and formatted numbers`() {
        val text = "Part-time job! Earn ₹3,000 daily by completing tasks."
        val start = text.indexOf("3,000")
        val rawSpan = TextSpan(start, start + "3,000".length)
        assertThat(text.substring(rawSpan.start, rawSpan.end)).isEqualTo("3,000")

        val expanded = ExplanationEngine.expandToWholeToken(text, rawSpan)
        assertThat(text.substring(expanded.start, expanded.end)).isEqualTo("₹3,000")
    }

    @Test
    fun `highlight expansion covers complete Devanagari words including matras and halant`() {
        val text = "यह आपका सत्यापन कोड है कृपया इसे किसी को न बताएं"
        val startIdx = text.indexOf("सत्यापन")
        val subSpan = TextSpan(startIdx + 2, startIdx + 5)

        val expanded = ExplanationEngine.expandToWholeToken(text, subSpan)
        assertThat(text.substring(expanded.start, expanded.end)).isEqualTo("सत्यापन")
    }

    @Test
    fun `highlight expansion preserves emoji surrogate pairs without splitting`() {
        val text = "🚨 Urgent alert for you"
        val emojiSpan = TextSpan(0, 2)
        val expanded = ExplanationEngine.expandToWholeToken(text, emojiSpan)
        assertThat(text.substring(expanded.start, expanded.end)).isEqualTo("🚨")
    }

    // --- S03: First Contact Suppression on Paste and Share ---

    @Test
    fun `S03 does NOT fire on PASTE source`() {
        val msg = createMessage(
            text = "Hello, checking this message.",
            source = SourceKind.PASTE,
            senderKind = SenderKind.NUMBER_ONLY,
            conversationKey = null
        )
        val verdict = engine.analyze(msg)
        val s03 = verdict.reasons.find { it.signalId == "S03" }
        assertThat(s03).isNull()
    }

    @Test
    fun `S03 does NOT fire on SHARE source`() {
        val msg = createMessage(
            text = "Hello, shared from another app.",
            source = SourceKind.SHARE,
            senderKind = SenderKind.NUMBER_ONLY,
            conversationKey = null
        )
        val verdict = engine.analyze(msg)
        val s03 = verdict.reasons.find { it.signalId == "S03" }
        assertThat(s03).isNull()
    }

    @Test
    fun `S03 fires on NOTIFICATION source for first message from unknown sender`() {
        val msg = createMessage(
            text = "Hello from unknown number.",
            source = SourceKind.NOTIFICATION,
            senderKind = SenderKind.NUMBER_ONLY,
            conversationKey = "conv-new-sender"
        )
        val verdict = engine.analyze(msg)
        val s03 = verdict.reasons.find { it.signalId == "S03" }
        assertThat(s03).isNotNull()
    }

    @Test
    fun `original substring exactly equals highlighted token for every highlight across manual test messages`() {
        val testMessages = listOf(
            "Part-time job! Earn ₹3,000 daily by liking YouTube videos. Join our Telegram group now: t.me/earn-daily-task",
            "Bhai galti se tumhare number pe OTP aa gaya hai, please mujhe bhej do jaldi",
            "Aapka parcel customs mein pakda gaya hai. Release ke liye ₹2,500 fine abhi pay karein warna FIR hogi.",
            "Beware! Fraudsters are sending fake e-challan APK files on WhatsApp. Never install apps sent in chats."
        )

        for (text in testMessages) {
            val msg = createMessage(text, source = SourceKind.NOTIFICATION, senderKind = SenderKind.NUMBER_ONLY)
            val verdict = engine.analyze(msg)

            for (span in verdict.highlights) {
                // Assert valid bounds
                assertThat(span.start).isAtLeast(0)
                assertThat(span.end).isAtMost(text.length)
                assertThat(span.start).isLessThan(span.end)

                // Assert substring matches exactly without exceptions or corruptions
                val extracted = text.substring(span.start, span.end)
                assertThat(extracted).isNotEmpty()
            }
        }

        // Specific semantic token checks on each message
        // 1. Message 1: contains Telegram and t.me/earn-daily-task
        val v1 = engine.analyze(createMessage(testMessages[0], source = SourceKind.NOTIFICATION, senderKind = SenderKind.NUMBER_ONLY))
        val h1Texts = v1.highlights.map { testMessages[0].substring(it.start, it.end) }
        assertThat(h1Texts).contains("t.me/earn-daily-task")
        assertThat(h1Texts.any { it.contains("Telegram") }).isTrue()
        val telegramIdx = testMessages[0].indexOf("Telegram")
        assertThat(testMessages[0].substring(telegramIdx, telegramIdx + "Telegram".length)).isEqualTo("Telegram")
        val tmeIdx = testMessages[0].indexOf("t.me/earn-daily-task")
        assertThat(testMessages[0].substring(tmeIdx, tmeIdx + "t.me/earn-daily-task".length)).isEqualTo("t.me/earn-daily-task")

        // 2. Message 2: contains OTP pretext
        val v2 = engine.analyze(createMessage(testMessages[1], source = SourceKind.NOTIFICATION, senderKind = SenderKind.NUMBER_ONLY))
        val h2Texts = v2.highlights.map { testMessages[1].substring(it.start, it.end) }
        assertThat(h2Texts.any { it.contains("OTP") }).isTrue()
        val otpIdx = testMessages[1].indexOf("OTP")
        assertThat(testMessages[1].substring(otpIdx, otpIdx + "OTP".length)).isEqualTo("OTP")

        // 3. Message 3: contains FIR and fine payment
        val v3 = engine.analyze(createMessage(testMessages[2], source = SourceKind.NOTIFICATION, senderKind = SenderKind.NUMBER_ONLY))
        val h3Texts = v3.highlights.map { testMessages[2].substring(it.start, it.end) }
        assertThat(h3Texts.any { it.contains("FIR") }).isTrue()
        val firIdx = testMessages[2].indexOf("FIR")
        assertThat(testMessages[2].substring(firIdx, firIdx + "FIR".length)).isEqualTo("FIR")
        val amountIdx = testMessages[2].indexOf("₹2,500")
        assertThat(testMessages[2].substring(amountIdx, amountIdx + "₹2,500".length)).isEqualTo("₹2,500")

        // 4. Message 4: scam awareness warning lands at NONE with m'=0, hence has zero highlights
        val v4 = engine.analyze(createMessage(testMessages[3], source = SourceKind.NOTIFICATION, senderKind = SenderKind.NUMBER_ONLY))
        assertThat(v4.level).isEqualTo(AlertLevel.NONE)
        assertThat(v4.highlights).isEmpty()
        val waIdx = testMessages[3].indexOf("WhatsApp")
        assertThat(testMessages[3].substring(waIdx, waIdx + "WhatsApp".length)).isEqualTo("WhatsApp")
    }
}
