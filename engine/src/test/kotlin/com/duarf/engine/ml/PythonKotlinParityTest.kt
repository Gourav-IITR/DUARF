package com.duarf.engine.ml

import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class PythonKotlinParityTest {

    private lateinit var classifier: LinearClassifier
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val modelFile = File(packsDir, "model/model.bin")
        assertThat(modelFile.exists()).isTrue()
        classifier = LinearClassifier.fromBytes(modelFile.readBytes())
        featurizer = Featurizer()

        val packs = PackLoader.load(FilePackSource(packsDir))
        val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
    }

    @Test
    fun `export round-trip matches python reference within 0_01`() {
        // Sample message from train split: row 0
        val text = "Shadi ka card dekhne ke liye bank_security_759.apk install karein aur sabhi photos dekhein. Thank you."
        val msg = IncomingMessage(
            fingerprint = "trn-scam-apk-06-0020",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-0",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(text)
        val extracted = entityExtractor.extract(normalized)
        val feat = featurizer.featurize(msg, normalized, extracted)

        val pred = classifier.predict(feat)

        // Python reference probability: 0.9754
        val pythonProb = 0.9754
        assertThat(pred.probability).isWithin(0.01).of(pythonProb)
    }
}
