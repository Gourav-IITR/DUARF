package com.duarf.engine.ml

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelLoaderTest {

    private lateinit var packsDir: File
    private lateinit var modelBytes: ByteArray
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor

    @Before
    fun setUp() {
        packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val modelFile = File(packsDir, "model/model.bin")
        assertThat(modelFile.exists()).isTrue()
        modelBytes = modelFile.readBytes()
        featurizer = Featurizer()

        val packs = PackLoader.load(FilePackSource(packsDir))
        val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
    }

    @Test
    fun `model file size is derived dynamically from header and matches 262176 bytes`() {
        val classifier = LinearClassifier.fromBytes(modelBytes)
        assertThat(classifier.formatVersion).isEqualTo(1)
        assertThat(classifier.featurizerVersion).isEqualTo(1)
        assertThat(classifier.log2Buckets).isEqualTo(18)

        // Formula: 28 + (1 shl log2Buckets) + 4
        val expectedSize = 28 + (1 shl classifier.log2Buckets) + 4
        assertThat(expectedSize).isEqualTo(262176)
        assertThat(modelBytes.size).isEqualTo(expectedSize)
    }

    @Test
    fun `truncated or corrupted model file throws IllegalArgumentException`() {
        // Truncated file
        val truncated = modelBytes.copyOfRange(0, 1000)
        assertThrows(IllegalArgumentException::class.java) {
            LinearClassifier.fromBytes(truncated)
        }

        // Wrong magic
        val wrongMagic = modelBytes.clone()
        wrongMagic[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            LinearClassifier.fromBytes(wrongMagic)
        }

        // CRC mismatch (flipped byte in weights)
        val corruptedWeights = modelBytes.clone()
        corruptedWeights[100] = (corruptedWeights[100] + 1).toByte()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            LinearClassifier.fromBytes(corruptedWeights)
        }
        assertThat(exception.message).contains("CRC32 mismatch")
    }

    @Test
    fun `rules-only fallback when model is absent`() {
        val packs = PackLoader.load(FilePackSource(packsDir))
        val rulesOnlyPacks = packs.copy(modelBytes = null)
        val engine = DefaultScamEngine(rulesOnlyPacks)

        val msg = IncomingMessage(
            fingerprint = "fallback-test",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-fallback",
            senderDisplay = "Friend",
            senderKind = SenderKind.NAMED,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Hey are we still meeting for lunch at 1 PM?",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val verdict = engine.analyze(msg)
        assertThat(verdict.modelProbability).isNull()
        assertThat(verdict.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `model prediction differentiates obvious scam vs benign`() {
        val classifier = LinearClassifier.fromBytes(modelBytes)

        val scamMsg = IncomingMessage(
            fingerprint = "scam-eval",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-scam",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Dear customer, your bank account at SBI is blocked. Download sbi_update.apk from http://sbi-kyc-verify.xyz/update immediately to unfreeze.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val scamNorm = TextNormalizer.normalize(scamMsg.text)
        val scamExt = entityExtractor.extract(scamNorm)
        val scamPred = classifier.predict(featurizer.featurize(scamMsg, scamNorm, scamExt))

        assertThat(scamPred.probability).isAtLeast(0.70)
        assertThat(scamPred.mPrime).isGreaterThan(0.20)
        // Check attribution highlights emitted when m' > 0.2
        assertThat(scamPred.highlights.size).isIn(1..5)

        val benignMsg = IncomingMessage(
            fingerprint = "benign-eval",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-benign",
            senderDisplay = "Mom",
            senderKind = SenderKind.NAMED,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Beta, please bring vegetables while coming back from office today. Dinner is ready.",
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
        val benignNorm = TextNormalizer.normalize(benignMsg.text)
        val benignExt = entityExtractor.extract(benignNorm)
        val benignPred = classifier.predict(featurizer.featurize(benignMsg, benignNorm, benignExt))

        assertThat(benignPred.probability).isLessThan(0.30)
        assertThat(benignPred.mPrime).isEqualTo(0.0)
        assertThat(benignPred.highlights).isEmpty()
    }
}
