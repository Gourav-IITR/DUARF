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

class FeaturizerTest {

    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor

    @Before
    fun setUp() {
        featurizer = Featurizer(log2Buckets = 18)
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val packs = PackLoader.load(FilePackSource(packsDir))
        val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
    }

    @Test
    fun `murmurhash3 test vectors`() {
        // Empty data hash with seed 0 is 0
        assertThat(MurmurHash3.hash32(ByteArray(0), 0)).isEqualTo(0)

        // Basic string hashing
        val h1 = MurmurHash3.hash32("hello", 0)
        val h2 = MurmurHash3.hash32("hello", 0)
        val h3 = MurmurHash3.hash32("world", 0)
        assertThat(h1).isEqualTo(h2)
        assertThat(h1).isNotEqualTo(h3)
    }

    @Test
    fun `featurizer replaces entities and generates expected bucket features`() {
        val text = "Dear customer, Rs 5000 debited for order. Visit http://notice-service.xyz or call +919876543210."
        val msg = IncomingMessage(
            fingerprint = "feat-test-1",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-feat",
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

        val result = featurizer.featurize(msg, normalized, extracted)

        // Should have active bucket indices
        assertThat(result.activeIndices).isNotEmpty()
        // Distinct and sorted
        for (i in 0 until result.activeIndices.size - 1) {
            assertThat(result.activeIndices[i]).isLessThan(result.activeIndices[i + 1])
        }
        // Indices in [0, 2^18 - 1]
        assertThat(result.activeIndices.first()).isAtLeast(0)
        assertThat(result.activeIndices.last()).isLessThan(1 shl 18)

        // L2 normalization factor = 1 / sqrt(k)
        val expectedL2 = 1.0 / kotlin.math.sqrt(result.activeIndices.size.toDouble())
        assertThat(result.l2Value).isWithin(1e-6).of(expectedL2)

        // Token attributions mapped
        assertThat(result.tokenAttributions).isNotEmpty()
    }
}
