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

class SignalDeduplicationTest {

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

    @Test
    fun testSameBrandInHeaderAndText_deduplicatesSignals() {
        // Message with SBI claimed in header and also in text with an unofficial URL
        val msg = IncomingMessage(
            fingerprint = "msg-dup-sbi",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GENERIC,
            conversationKey = "conv-sbi-dup",
            senderDisplay = "VK-SBIBNK",
            senderKind = SenderKind.DLT_HEADER,
            senderCountryCode = "+91",
            isGroup = false,
            text = "SBI alert: Dear State Bank of India user, your KYC expired. Update at http://sbi-kyc-verify.xyz immediately.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)

        // Ensure multiple mentions of SBI exist in text
        val sbiBrands = extracted.brands.filter { it.brandName == "sbi" }
        assertThat(sbiBrands.size).isAtLeast(2)

        val (signals, _) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        // Count occurrences of L02 (brand_domain_mismatch)
        val l02Count = signals.count { it.signalId == "L02" }
        assertThat(l02Count).isEqualTo(1)

        // Check that all evidence spans are retained in allEvidenceSpans
        val l02Signal = signals.first { it.signalId == "L02" }
        assertThat(l02Signal.allEvidenceSpans.size).isAtLeast(1)

        // Run full analysis
        val verdict = engine.analyze(msg)
        val l02Reasons = verdict.reasons.filter { it.signalId == "L02" }
        assertThat(l02Reasons.size).isAtMost(1)
    }

    @Test
    fun testPoliceBrandInTextAndUrl_deduplicatesL02AndL09() {
        // Police case scam with "Delhi Police" in text and "delhipolice" in URL
        val msg = IncomingMessage(
            fingerprint = "msg-police-dup",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-police-dup",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Delhi Police: FIR registered against you. Pay penalty immediately at https://delhipolice-portal.xyz/pay",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)

        val (signals, _) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        // L02 and L09 should each appear at most ONCE
        assertThat(signals.count { it.signalId == "L02" }).isAtMost(1)
        assertThat(signals.count { it.signalId == "L09" }).isAtMost(1)

        // Check that highlights in verdict include the evidence
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.highlights).isNotEmpty()
    }
}
