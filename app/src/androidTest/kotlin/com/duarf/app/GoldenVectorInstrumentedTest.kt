package com.duarf.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duarf.app.pack.AssetPackSource
import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.ml.Featurizer
import com.duarf.engine.ml.GoldenVectorEntry
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test executing on the real Android runtime (ART / ICU regex engine).
 *
 * Verifies that:
 * 1. DefaultScamEngine builds successfully from real Android APK assets without crashing or throwing.
 * 2. All 200 committed golden vectors produce the EXACT same feature indices and scores on Android ICU
 *    as on the JVM, guaranteeing zero divergence between offline training and on-device execution (Invariant 8).
 */
@RunWith(AndroidJUnit4::class)
class GoldenVectorInstrumentedTest {

    @Test
    fun testGoldenVectorsOnAndroidRuntime() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val packSource = AssetPackSource(appContext)
        val loadedPacks = PackLoader.load(packSource)
        val engine = DefaultScamEngine(loadedPacks)
        val featurizer = Featurizer()
        val psl = if (loadedPacks.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(loadedPacks.pslLines.asSequence()) else PublicSuffixList()
        val entityExtractor = EntityExtractor(psl, loadedPacks.brands, loadedPacks.upiHandles)

        val goldenJson = appContext.assets.open("packs/golden_vectors.json").bufferedReader().use { it.readText() }
        val json = Json { ignoreUnknownKeys = true }
        val entries = json.decodeFromString<List<GoldenVectorEntry>>(goldenJson)
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

            assertWithMessage("Android ICU featurizer index mismatch for entry #$index (${entry.id})")
                .that(feat.activeIndices)
                .isEqualTo(entry.activeIndices)

            assertWithMessage("Android ICU featurizer L2 mismatch for entry #$index (${entry.id})")
                .that(feat.l2Value)
                .isWithin(1e-6)
                .of(entry.l2Value)

            val verdict = engine.analyze(msg)
            assertWithMessage("Android engine verdict level mismatch for entry #$index (${entry.id})")
                .that(verdict.level.name)
                .isEqualTo(entry.expectedLevel)

            assertWithMessage("Android engine score mismatch for entry #$index (${entry.id})")
                .that(verdict.score)
                .isWithin(1e-3)
                .of(entry.expectedScore)
        }
    }
}
