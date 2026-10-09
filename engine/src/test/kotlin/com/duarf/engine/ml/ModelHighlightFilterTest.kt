// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.ml

import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import com.duarf.engine.normalize.TextNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelHighlightFilterTest {

    private lateinit var classifier: LinearClassifier
    private val featurizer = Featurizer()
    private lateinit var entityExtractor: EntityExtractor

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val modelFile = File(packsDir, "model/model.bin")
        classifier = LinearClassifier.fromBytes(modelFile.readBytes())

        val packs = com.duarf.engine.pack.PackLoader.load(com.duarf.engine.pack.FilePackSource(packsDir))
        val psl = if (packs.pslLines.isNotEmpty()) com.duarf.engine.extract.PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else com.duarf.engine.extract.PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
    }

    @Test
    fun `isMeaningfulModelToken filters tokens under 3 characters`() {
        assertThat(Stopwords.isMeaningfulModelToken("to")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("in")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("at")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("is")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("se")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("ko")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("ka")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("ki")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("के")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("पर")).isFalse()
    }

    @Test
    fun `isMeaningfulModelToken filters stopwords across en, hi, and hi-Latn`() {
        // English stopwords
        assertThat(Stopwords.isMeaningfulModelToken("will")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("this")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("that")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("have")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("with")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("please")).isFalse()

        // Hindi stopwords
        assertThat(Stopwords.isMeaningfulModelToken("लेकिन")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("क्योंकि")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("अपना")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("सकता")).isFalse()

        // Hindi-Latin stopwords
        assertThat(Stopwords.isMeaningfulModelToken("lekin")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("kyunki")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("apna")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("tumhara")).isFalse()
    }

    @Test
    fun `isMeaningfulModelToken drops featurizer internal placeholders`() {
        assertThat(Stopwords.isMeaningfulModelToken("__url__")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("__phone__")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("__code__")).isFalse()
        assertThat(Stopwords.isMeaningfulModelToken("__file_apk__")).isFalse()
    }

    @Test
    fun `isMeaningfulModelToken retains meaningful scam content tokens`() {
        assertThat(Stopwords.isMeaningfulModelToken("bill")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("disconnect")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("electricity")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("unpaid")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("arrest")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("customs")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("बिजली")).isTrue()
        assertThat(Stopwords.isMeaningfulModelToken("गिरफ्तारी")).isTrue()
    }

    @Test
    fun `model prediction highlights for utility scam exclude stopwords and short tokens`() {
        val text = "Dear consumer, Electricity Board will disconnect your power supply tonight at 9:30 PM due to unpaid bill. Call 9876543210 to pay immediately."
        val msg = IncomingMessage(
            fingerprint = "test-case-b",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GOOGLE_MESSAGES,
            conversationKey = "conv-sms-b",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.PERSONAL_NUMBER,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val norm = TextNormalizer.normalize(msg.text)
        val ext = entityExtractor.extract(norm)
        val pred = classifier.predict(featurizer.featurize(msg, norm, ext))

        assertThat(pred.mPrime).isGreaterThan(0.2)
        assertThat(pred.highlights).isNotEmpty()

        // Extract highlighted substrings
        val highlightedWords = pred.highlights.map { text.substring(it.start, it.end) }

        // Must NOT contain stopwords or short tokens
        assertThat(highlightedWords).doesNotContain("will")
        assertThat(highlightedWords).doesNotContain("to")
        assertThat(highlightedWords).doesNotContain("at")
        assertThat(highlightedWords).doesNotContain("is")
        assertThat(highlightedWords).doesNotContain("due") // 3 chars but "due" vs "to"

        // Every highlighted token must be >= 3 chars and not a stopword
        for (w in highlightedWords) {
            assertThat(w.length).isAtLeast(3)
            assertThat(Stopwords.ALL_STOPWORDS).doesNotContain(w.lowercase())
        }
    }
}
