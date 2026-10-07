package com.duarf.engine

import com.duarf.engine.extract.BrandDefinition
import com.duarf.engine.extract.BrandKind
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class UnverifiedBrandSuppressionTest {

    private lateinit var engine: DefaultScamEngine

    private val verifiedBrand = BrandDefinition(
        id = "test_verified_bank",
        kind = BrandKind.BANK,
        names = listOf("TestVerifiedBank", "VerifiedBank"),
        officialDomains = listOf("verifiedbank.com"),
        source = "https://verifiedbank.com",
        verifiedOn = "2026-10-06",
        isVerified = true
    )

    private val unverifiedBrand = BrandDefinition(
        id = "test_unverified_bank",
        kind = BrandKind.BANK,
        names = listOf("TestUnverifiedBank", "UnverifiedBank"),
        officialDomains = listOf("unverifiedbank.com"),
        source = "https://unverifiedbank.com",
        verifiedOn = "2026-10-06",
        isVerified = false
    )

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val loadedPacks = PackLoader.load(FilePackSource(rootDir))
        // Combine loaded brands with our explicit verified and unverified test brands
        val updatedBrands = loadedPacks.brands + listOf(verifiedBrand, unverifiedBrand)
        engine = DefaultScamEngine(loadedPacks.copy(brands = updatedBrands))
    }

    private fun createMessage(
        text: String,
        app: SourceApp = SourceApp.WHATSAPP,
        senderKind: SenderKind = SenderKind.NUMBER_ONLY,
        senderDisplay: String = "+919876543210",
        dltBrand: String? = null,
        dltSuffix: String? = null
    ): IncomingMessage {
        return IncomingMessage(
            fingerprint = "test-fp",
            source = SourceKind.NOTIFICATION,
            app = app,
            conversationKey = "conv-1",
            senderDisplay = senderDisplay,
            senderKind = senderKind,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis(),
            dltHeaderBrand = dltBrand,
            dltHeaderSuffix = dltSuffix
        )
    }

    @Test
    fun `unverified brand suppresses L02 brand domain mismatch`() {
        // Unverified brand paired with unofficial domain must NOT fire L02
        val unverifiedMsg = createMessage("Notice from UnverifiedBank: please visit http://evilphish.com/verify to update KYC.")
        val unverifiedVerdict = engine.analyze(unverifiedMsg)
        assertThat(unverifiedVerdict.reasons.map { it.signalId }).doesNotContain("L02")

        // In contrast, verified brand paired with unofficial domain fires L02
        val verifiedMsg = createMessage("Notice from VerifiedBank: please visit http://evilphish.com/verify to update KYC.")
        val verifiedVerdict = engine.analyze(verifiedMsg)
        assertThat(verifiedVerdict.reasons.map { it.signalId }).contains("L02")
    }

    @Test
    fun `unverified brand suppresses L03 lookalike domain`() {
        // Unverified brand paired with lookalike domain must NOT fire L03
        val unverifiedMsg = createMessage("Alert from UnverifiedBank: log in at http://unverifiedbankk.com/login.")
        val unverifiedVerdict = engine.analyze(unverifiedMsg)
        assertThat(unverifiedVerdict.reasons.map { it.signalId }).doesNotContain("L03")

        // Verified brand fires L03
        val verifiedMsg = createMessage("Alert from VerifiedBank: log in at http://verifiedbankk.com/login.")
        val verifiedVerdict = engine.analyze(verifiedMsg)
        assertThat(verifiedVerdict.reasons.map { it.signalId }).contains("L03")
    }

    @Test
    fun `unverified brand does not count for B02 official domains only`() {
        // Message with unverified brand and its domain: B02 must NOT fire
        val unverifiedMsg = createMessage("Update from UnverifiedBank: check your statement at http://unverifiedbank.com/statement.")
        val unverifiedVerdict = engine.analyze(unverifiedMsg)

        // Message with verified brand and its domain: B02 fires
        val verifiedMsg = createMessage("Update from VerifiedBank: check your statement at http://verifiedbank.com/statement.")
        val verifiedVerdict = engine.analyze(verifiedMsg)

        // Verified message ruleScore is dampened by B02 (factor 0.30), unverified is not dampened
        assertThat(verifiedVerdict.ruleScore).isLessThan(unverifiedVerdict.ruleScore)
    }

    @Test
    fun `unverified brand does not count for B06 verified header consistent`() {
        // SMS from -T header mentioning unverified brand must NOT fire B06
        val unverifiedSms = createMessage(
            text = "Notice from UnverifiedBank: urgent action required.",
            app = SourceApp.SMS_GENERIC,
            senderKind = SenderKind.DLT_HEADER,
            senderDisplay = "VM-VERIFIEDBANK-T",
            dltBrand = "VERIFIEDBANK",
            dltSuffix = "T"
        )
        val unverifiedVerdict = engine.analyze(unverifiedSms)

        // SMS from -T header mentioning verified brand fires B06
        val verifiedSms = createMessage(
            text = "Notice from VerifiedBank: urgent action required.",
            app = SourceApp.SMS_GENERIC,
            senderKind = SenderKind.DLT_HEADER,
            senderDisplay = "VM-VERIFIEDBANK-T",
            dltBrand = "VERIFIEDBANK",
            dltSuffix = "T"
        )
        val verifiedVerdict = engine.analyze(verifiedSms)

        // Verified message receives B06 dampener (factor 0.50), unverified message does not
        assertThat(verifiedVerdict.ruleScore).isLessThan(unverifiedVerdict.ruleScore)
    }
}
