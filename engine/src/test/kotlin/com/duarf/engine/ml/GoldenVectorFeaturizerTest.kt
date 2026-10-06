package com.duarf.engine.ml

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import java.io.File

@Serializable
private data class SeedCorpusRow(
    val id: String,
    val text: String,
    val label: String,
    val category: String,
    val lang: String,
    val sender_kind: String,
    val is_group: Boolean
)

class GoldenVectorFeaturizerTest {

    private lateinit var packsDir: File
    private lateinit var goldenFile: File
    private lateinit var corpusFile: File
    private lateinit var engine: DefaultScamEngine
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Before
    fun setUp() {
        packsDir = listOf(File("packs"), File("../packs"), File("../../packs")).first { it.exists() && File(it, "rules.json").exists() }
        goldenFile = File(packsDir, "golden_vectors.json")
        corpusFile = listOf(File("eval/corpus.jsonl"), File("../eval/corpus.jsonl"), File("../../eval/corpus.jsonl")).first { it.exists() }

        val loadedPacks = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(loadedPacks)
        featurizer = Featurizer()
        val psl = if (loadedPacks.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(loadedPacks.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, loadedPacks.brands, loadedPacks.upiHandles)
    }

    @Test
    fun `200 fixed texts produce committed golden feature indices and verdicts exactly`() {
        if (!goldenFile.exists()) {
            generateGoldenVectors()
        }

        val entries = json.decodeFromString<List<GoldenVectorEntry>>(goldenFile.readText())
        assertThat(entries).hasSize(200)

        for ((index, entry) in entries.withIndex()) {
            val sKind = if (entry.senderKind.equals("NAMED", ignoreCase = true)) SenderKind.NAMED else SenderKind.NUMBER_ONLY
            val msg = IncomingMessage(
                fingerprint = entry.id,
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv_${entry.id}",
                senderDisplay = if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
                senderKind = sKind,
                senderCountryCode = "+91",
                isGroup = entry.isGroup,
                text = entry.text,
                attachmentHint = null,
                receivedAtMillis = 1000000L
            )

            val normalized = TextNormalizer.normalize(msg.text)
            val extracted = entityExtractor.extract(normalized)
            val feat = featurizer.featurize(msg, normalized, extracted)

            // 1. Assert feature indices match exactly
            com.google.common.truth.Truth.assertWithMessage("Active indices mismatch for entry #$index (${entry.id})")
                .that(feat.activeIndices)
                .isEqualTo(entry.activeIndices)

            // 2. Assert L2 value matches
            com.google.common.truth.Truth.assertWithMessage("L2 mismatch for entry #$index (${entry.id})")
                .that(feat.l2Value)
                .isWithin(1e-6)
                .of(entry.l2Value)

            // 3. Assert engine verdict level matches
            val verdict = engine.analyze(msg)
            com.google.common.truth.Truth.assertWithMessage("Verdict level mismatch for entry #$index (${entry.id})")
                .that(verdict.level.name)
                .isEqualTo(entry.expectedLevel)

            // 4. Assert engine score matches within precision tolerance
            com.google.common.truth.Truth.assertWithMessage("Verdict score mismatch for entry #$index (${entry.id})")
                .that(verdict.score)
                .isWithin(1e-3)
                .of(entry.expectedScore)
        }
    }

    private fun generateGoldenVectors() {
        val rows = corpusFile.readLines()
            .filter { it.isNotBlank() && !it.trim().startsWith("#") }
            .take(200)
            .map { json.decodeFromString<SeedCorpusRow>(it) }

        val entries = ArrayList<GoldenVectorEntry>()
        for (row in rows) {
            val sKind = if (row.sender_kind.equals("NAMED", ignoreCase = true)) SenderKind.NAMED else SenderKind.NUMBER_ONLY
            val msg = IncomingMessage(
                fingerprint = row.id,
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv_${row.id}",
                senderDisplay = if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
                senderKind = sKind,
                senderCountryCode = "+91",
                isGroup = row.is_group,
                text = row.text,
                attachmentHint = null,
                receivedAtMillis = 1000000L
            )

            val normalized = TextNormalizer.normalize(msg.text)
            val extracted = entityExtractor.extract(normalized)
            val feat = featurizer.featurize(msg, normalized, extracted)
            val verdict = engine.analyze(msg)

            entries.add(
                GoldenVectorEntry(
                    id = row.id,
                    text = row.text,
                    senderKind = row.sender_kind,
                    isGroup = row.is_group,
                    activeIndices = feat.activeIndices,
                    l2Value = Math.round(feat.l2Value * 1000000.0) / 1000000.0,
                    expectedLevel = verdict.level.name,
                    expectedScore = Math.round(verdict.score * 1000.0) / 1000.0
                )
            )
        }

        goldenFile.writeText(json.encodeToString(entries))
    }
}
