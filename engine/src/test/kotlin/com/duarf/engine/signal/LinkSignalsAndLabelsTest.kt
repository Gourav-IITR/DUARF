// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.signal

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class LinkSignalsAndLabelsTest {

    private lateinit var engine: DefaultScamEngine

    @Before
    fun setUp() {
        val packsDir = listOf(File("packs"), File("../packs"), File("../../packs"))
            .firstOrNull { it.exists() && File(it, "rules.json").exists() }
            ?: File("packs")
        val loadedPacks = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(loadedPacks)
    }

    @Test
    fun testSignalIdToNameMappingForEveryLinkSignal() {
        val packsDir = listOf(File("packs"), File("../packs"), File("../../packs"))
            .firstOrNull { it.exists() && File(it, "rules.json").exists() }
            ?: File("packs")
        val loadedPacks = PackLoader.load(FilePackSource(packsDir))

        val expectedLSignals = mapOf(
            "L01" to "apk_file_or_link",
            "L02" to "brand_domain_mismatch",
            "L03" to "lookalike_domain",
            "L04" to "ip_literal_url",
            "L05" to "url_shortener",
            "L06" to "risky_tld",
            "L07" to "punycode_or_mixed_script_domain",
            "L08" to "obfuscated_url",
            "L09" to "gov_claim_non_gov_domain",
            "L10" to "url_userinfo_trick",
            "L11" to "blocklisted_domain",
            "L12" to "redirect_to_other_chat"
        )

        val actualSignals = loadedPacks.rules.signals.associate { it.id to it.name }

        for ((id, expectedName) in expectedLSignals) {
            assertThat(actualSignals[id]).isEqualTo(expectedName)
        }
    }

    @Test
    fun testApkFilenameAlone_firesL01Only_noL02NoL03() {
        // Plain APK filename from number-only sender with no URLs
        val msg = IncomingMessage(
            fingerprint = "msg-apk-alone",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-apk-alone",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Please find the event invitation: wedding_card.pdf.apk",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val verdict = engine.analyze(msg)
        val signalIds = verdict.reasons.map { it.signalId }.toSet()

        assertThat(signalIds).contains("L01")
        assertThat(signalIds).doesNotContain("L02")
        assertThat(signalIds).doesNotContain("L03")
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun testSbiUpdateApkFilename_firesL01_noDomainSignals() {
        // "sbi-update.apk" mentions brand SBI and has an APK filename, but no URL
        val msg = IncomingMessage(
            fingerprint = "msg-sbi-apk",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-sbi-apk",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "SBI urgent security alert: Please install sbi-update.apk on your phone immediately",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val verdict = engine.analyze(msg)
        val signalIds = verdict.reasons.map { it.signalId }.toSet()

        // L01 must fire for APK file
        assertThat(signalIds).contains("L01")
        // NO domain signals (L02..L11) should fire because there is no URL
        val domainSignals = setOf("L02", "L03", "L04", "L05", "L06", "L07", "L08", "L09", "L10", "L11", "L12")
        for (sig in domainSignals) {
            assertThat(signalIds).doesNotContain(sig)
        }
    }

    @Test
    fun testRegionalChallanApk_firesL01Only_noDomainSignals() {
        val teMsg = IncomingMessage(
            fingerprint = "msg-te-challan",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-te-challan",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "ట్రాఫిక్ పోలీస్: పెండింగ్ చలానా చెల్లించడానికి echallan_ts.apk ఇన్‌స్టాల్ చేయండి. చలానా నెం: 482910",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val teVerdict = engine.analyze(teMsg)
        val teSignals = teVerdict.reasons.map { it.signalId }.toSet()
        assertThat(teSignals).contains("L01")
        assertThat(teSignals).doesNotContain("L02")
        assertThat(teSignals).doesNotContain("L03")

        val taMsg = IncomingMessage(
            fingerprint = "msg-ta-challan",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-ta-challan",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "போக்குவரத்து காவல்: நிலுவையில் உள்ள அபராதத்தை செலுத்த echallan_tn.apk நிறுவவும். எண்: 577463",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val taVerdict = engine.analyze(taMsg)
        val taSignals = taVerdict.reasons.map { it.signalId }.toSet()
        assertThat(taSignals).contains("L01")
        assertThat(taSignals).doesNotContain("L02")
        assertThat(taSignals).doesNotContain("L03")
    }

    @Test
    fun testSchemelessZipAndAppDomains_parsedAsDomains_evaluateDomainSignals() {
        // .zip and .app are real ICANN TLDs, not executable extensions.
        // Schemeless occurrences must be parsed as domains and evaluate domain signals (e.g. L02).
        val zipMsg = IncomingMessage(
            fingerprint = "msg-zip-domain",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-zip-domain",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "SBI alert: Complete your mandatory KYC update at secure-login.zip immediately.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val zipVerdict = engine.analyze(zipMsg)
        val zipSignals = zipVerdict.reasons.map { it.signalId }.toSet()
        // Must evaluate domain signals: L02 (brand_domain_mismatch for SBI against secure-login.zip)
        assertThat(zipSignals).contains("L02")
        // Not a file executable
        assertThat(zipSignals).doesNotContain("L01")

        val appMsg = IncomingMessage(
            fingerprint = "msg-app-domain",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-app-domain",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "HDFC Bank security alert: Verify your netbanking account at verify.app to prevent account freeze.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val appVerdict = engine.analyze(appMsg)
        val appSignals = appVerdict.reasons.map { it.signalId }.toSet()
        assertThat(appSignals).contains("L02")
        assertThat(appSignals).doesNotContain("L01")
    }

    @Test
    fun testHttpUrlEndingInApk_firesL01AndHostDomainSignals() {
        // "http://x-bank.com/update.apk" -> L01 AND host-based signals on x-bank.com
        val msg = IncomingMessage(
            fingerprint = "msg-url-apk",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-url-apk",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "HDFC Bank: Security patch at http://x-bank.com/update.apk",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val normalized = com.duarf.engine.normalize.TextNormalizer.normalize(msg.text)
        val extracted = engine.entityExtractor.extract(normalized)
        val (allSignals, _) = engine.signalEngine.evaluate(msg, normalized, extracted)
        val signalIds = allSignals.map { it.signalId }.toSet()

        // L01 fires for .apk in URL path
        assertThat(signalIds).contains("L01")
        // Host-based signals evaluate on host x-bank.com (mismatch with HDFC Bank official domain)
        assertThat(signalIds).contains("L02")

        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun testBareEchallanApkFilename_firesL01Only_noDomainSignals() {
        val msg = IncomingMessage(
            fingerprint = "msg-bare-apk",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-bare-apk",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Traffic police: Pending e-challan notice. Install echallan_ts.apk immediately.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val verdict = engine.analyze(msg)
        val signals = verdict.reasons.map { it.signalId }.toSet()

        assertThat(signals).contains("L01")
        val domainSignals = setOf("L02", "L03", "L04", "L05", "L06", "L07", "L08", "L09", "L10", "L11", "L12")
        for (sig in domainSignals) {
            assertThat(signals).doesNotContain(sig)
        }
    }
}
