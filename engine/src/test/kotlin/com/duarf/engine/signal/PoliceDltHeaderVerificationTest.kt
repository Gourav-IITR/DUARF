package com.duarf.engine.signal

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.DltHeaderParser
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

class PoliceDltHeaderVerificationTest {

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

    private fun createSmsMessage(senderDisplay: String, text: String): IncomingMessage {
        val parsed = DltHeaderParser.parse(senderDisplay)
        return IncomingMessage(
            fingerprint = "msg-police-test",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GOOGLE_MESSAGES,
            conversationKey = "conv-police-test",
            senderDisplay = senderDisplay,
            senderKind = parsed.senderKind,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis(),
            dltHeaderPrefix = parsed.dltPrefix,
            dltHeaderBrand = parsed.dltBrand,
            dltHeaderSuffix = parsed.dltSuffix
        )
    }

    @Test
    fun testEmptyAllowlist_unverifiedHeadersDoNotEarnB06() {
        // With default empty allowlist, DLPOL and NCRP are unverified and do not earn B06
        val msg = createSmsMessage(
            senderDisplay = "DL-DLPOL-G",
            text = "Delhi Police: Your lost report LR/12345/2026 has been registered successfully. - Delhi Police"
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

        // Unverified header must NOT receive B06
        assertThat(dampenerIds).doesNotContain("B06")
        // Header claiming police without verified allowlist entry fires S05
        assertThat(signalIds).contains("S05")

        // Spec §10: S05 alone with no ask, link, or threat is capped below Caution threshold -> NONE
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun testUnverifiedPoliceHeader_withAskOrLink_notCapped() {
        // S05 paired with an upfront payment ask (A03) is NOT capped -> AlertLevel.CAUTION or above
        val msg = createSmsMessage(
            senderDisplay = "DL-DLPOL-G",
            text = "Delhi Police: Challan unpaid. Pay fine Rs 500 immediately to avoid arrest warrant."
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isAtLeast(AlertLevel.CAUTION)
    }

    @Test
    fun testConfiguredAllowlist_verifiedHeader_earnsB06() {
        // When an allowlist is explicitly verified and configured, B06 fires for genuine advisories
        val customPacks = loadedPacks.copy(policeDltHeaders = setOf("DLPOL", "NCRP"))
        val customEngine = DefaultScamEngine(customPacks)

        val msg = createSmsMessage(
            senderDisplay = "DL-NCRP-G",
            text = "National Cyber Crime Reporting Portal: Your complaint ACK/98765/2026 is registered. Track at cybercrime.gov.in - NCRP"
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)
        val (signals, dampeners) = customEngine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )

        val signalIds = signals.map { it.signalId }.toSet()
        val dampenerIds = dampeners.map { it.signalId }.toSet()

        assertThat(dampenerIds).contains("B06")
        assertThat(signalIds).doesNotContain("S05")

        val verdict = customEngine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun testUnlistedPoliceLookingHeader_advisoryMessage_noB06() {
        // KOLPOL is not in police_dlt_headers.txt allowlist -> unverified header -> cannot receive B06
        val msg = createSmsMessage(
            senderDisplay = "VK-KOLPOL-G",
            text = "Kolkata Police: Never share your OTP or banking PIN with anyone. Stay alert. - Kolkata Police"
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

        // Unlisted header must NOT receive B06
        assertThat(dampenerIds).doesNotContain("B06")
        // Pure advisory without ask/link does NOT fire S05
        assertThat(signalIds).doesNotContain("S05")

        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun testUnlistedPoliceLookingHeader_claimsPoliceWithAskOrLink_firesS05_andCombosApply() {
        // Unlisted police-looking header claiming police authority with a phishing link
        val msg = createSmsMessage(
            senderDisplay = "VK-KOLPOL-G",
            text = "Kolkata Police: Illegal drugs seized in your name. Download case file immediately: http://police-case-update.xyz"
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

        // S05, L02, L09, L06, P01 must fire
        assertThat(signalIds).containsAtLeast("S05", "L02", "L09", "L06", "P01")
        // No B06
        assertThat(dampenerIds).doesNotContain("B06")

        // Verdict must be DANGER via combos
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun testPoliceHeader_caseRegisteredCallNumberImmediately_notCapped() {
        // police-looking header + "case registered, call <number> immediately" -> NOT capped (Caution or above)
        val msg = createSmsMessage(
            senderDisplay = "DL-DLPOL-G",
            text = "Delhi Police: Case registered against your number. Call +919876543210 immediately to avoid arrest."
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isAtLeast(AlertLevel.CAUTION)
    }

    @Test
    fun testPoliceHeader_pureAdvisoryWithHelpline1930_none() {
        // police-looking header + pure advisory incl. "helpline 1930" -> NONE
        val msg = createSmsMessage(
            senderDisplay = "DL-DLPOL-G",
            text = "Delhi Police Cyber Cell: Cyber security advisory. Beware of suspicious APK files and loan offers. For reporting fraud, call national helpline 1930 or visit cybercrime.gov.in."
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun testScamText_with1930MentionAndOtpAskOrLink_unchangedLevel() {
        // scam text with "1930" mention + OTP ask or link -> unchanged level (awareness never suppresses A*/L*)
        val msg = createSmsMessage(
            senderDisplay = "VM-SBIBNK-T",
            text = "SBI Alert: National Cyber Helpline 1930 received an unauthorized complaint on your account. Share OTP 123456 now to verify: http://sbi-verify.phish.com"
        )
        val verdict = engine.analyze(msg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
    }
}
