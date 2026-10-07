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

class UpiPinProximityAndNegationTest {

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

    private fun evaluateSignals(text: String): Set<String> {
        val msg = IncomingMessage(
            fingerprint = "msg-pin-test",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-pin-test",
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
        val (signals, _) = engine.signalEngine.evaluate(
            message = msg,
            normalized = normalized,
            extracted = extracted
        )
        return signals.map { it.signalId }.toSet()
    }

    // Required tests that MUST fire A04

    @Test
    fun testMustFireA04_phrase1_pinDaloKisiKoMatBatanaPaiseAaJayenge() {
        // "mat" is attached to "batana", not PIN negation; distance <= 8 tokens -> MUST fire A04
        val signals = evaluateSignals("PIN dalo, kisi ko mat batana, paise aa jayenge")
        assertThat(signals).contains("A04")
    }

    @Test
    fun testMustFireA04_phrase2_sirfUpiPinDaliyeRefundTurantAaJayega() {
        // "Sirf" is an instruction adverb ("just enter PIN"); distance <= 8 tokens -> MUST fire A04
        val signals = evaluateSignals("Sirf UPI PIN daliye, refund turant aa jayega")
        assertThat(signals).contains("A04")
    }

    @Test
    fun testMustFireA04_phrase3_noChargesEnterUpiPinToReceiveYourRefund() {
        // "No" governs charges, not PIN; instruction + receive -> MUST fire A04
        val signals = evaluateSignals("No charges. Enter UPI PIN to receive your refund")
        assertThat(signals).contains("A04")
    }

    @Test
    fun testMustFireA04_phrase4_dontWorryJustScanTheQrAndEnterPinToGetYourCashback() {
        // "Don't" governs worry, not PIN; instruction + receive -> MUST fire A04
        val signals = evaluateSignals("Don't worry, just scan the QR and enter PIN to get your cashback")
        assertThat(signals).contains("A04")
    }

    // Required tests that MUST NOT fire A04

    @Test
    fun testMustNotFireA04_neverEnterYourUpiPinToReceiveMoney() {
        // Negation directly governs PIN requirement -> MUST NOT fire A04
        val signals = evaluateSignals("Never enter your UPI PIN to receive money")
        assertThat(signals).doesNotContain("A04")
    }

    @Test
    fun testMustNotFireA04_paisePaneKeLiyeUpiPinKiZaruratNahiHoti() {
        // "ki zarurat nahi" directly governs PIN requirement -> MUST NOT fire A04
        val signals = evaluateSignals("Paise pane ke liye UPI PIN ki zarurat nahi hoti")
        assertThat(signals).doesNotContain("A04")
    }

    // Additional informational / safety advisories in regional languages

    @Test
    fun testMustNotFireA04_hindiDevanagariSafetyNotice() {
        val signals = evaluateSignals("सावधानी: पैसे प्राप्त करने के लिए कभी भी यूपीआई पिन दर्ज न करें।")
        assertThat(signals).doesNotContain("A04")
    }

    @Test
    fun testMustNotFireA04_marathiSafetyNotice() {
        val signals = evaluateSignals("सावधान: पैसे मिळवण्यासाठी पिन टाकण्याची गरज नाही.")
        assertThat(signals).doesNotContain("A04")
    }

    @Test
    fun testMustNotFireA04_bengaliSafetyNotice() {
        val signals = evaluateSignals("সতর্কতা: টাকা পাওয়ার জন্য পিন দেবেন না।")
        assertThat(signals).doesNotContain("A04")
    }

    @Test
    fun testMustNotFireA04_teluguSafetyNotice() {
        val signals = evaluateSignals("హెచ్చరిక: డబ్బులు అందుకోవడానికి పిన్ అవసరం లేదు.")
        assertThat(signals).doesNotContain("A04")
    }

    // Mixed message: Advisory clause + real scam ask in another clause
    @Test
    fun testMixedMessage_advisoryPlusScamAsk_firesA04() {
        // Clause 1 has advisory; Clause 2 has scam ask -> A04 fires on Clause 2
        val signals = evaluateSignals(
            "RBI Warning: Never enter your UPI PIN to receive money. But your electricity bill is unpaid. Scan QR and enter PIN to get your cashback discount."
        )
        assertThat(signals).contains("A04")
    }
}
