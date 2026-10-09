// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.ml

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * JVM regression timing benchmark against Section 15 budgets.
 * Low-end on-device hardware timing is evaluated in Milestone M6.
 */
class InferenceBenchmarkTest {

    private lateinit var engine: DefaultScamEngine
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor
    private var classifier: LinearClassifier? = null

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val packs = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(packs)
        featurizer = Featurizer()
        val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
        classifier = packs.modelBytes?.let { LinearClassifier.fromBytes(it) }
    }

    @Test
    fun `jvm regression check - 1000 char message analysis within budget`() {
        // Build a realistic ~1,000-character message with multiple entities, paragraphs, and words
        val paragraph = "Dear customer, your bank account at HDFC Bank has experienced an unusual security event. " +
                "Please verify your identity immediately by visiting https://hdfc-bank-portal-update.xyz/verify " +
                "or calling our helpline at +919876543210. Failure to update within 24 hours will result in permanent " +
                "suspension of your netbanking and debit card services. Also do not share your OTP 492019 with anyone. " +
                "Regards, Customer Security Division. "
        val sb = StringBuilder()
        while (sb.length < 1000) {
            sb.append(paragraph)
        }
        val text1000 = sb.substring(0, 1000)
        assertThat(text1000.length).isEqualTo(1000)

        val msg = IncomingMessage(
            fingerprint = "bench-1000",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-bench",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text1000,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        // Warm up JVM (JIT compilation)
        for (i in 0 until 20) {
            engine.analyze(msg)
        }

        // Measure full engine analysis over 100 iterations
        val timingsMs = ArrayList<Double>()
        for (i in 0 until 100) {
            val elapsedNanos = measureNanoTime {
                engine.analyze(msg)
            }
            timingsMs.add(elapsedNanos / 1_000_000.0)
        }

        timingsMs.sort()
        val p95Ms = timingsMs[(timingsMs.size * 0.95).toInt()]
        val medianMs = timingsMs[timingsMs.size / 2]

        // Section 15 budget: p95 < 150 ms (JVM should be well below 25 ms)
        assertThat(p95Ms).isLessThan(150.0)

        // Measure ML featurization + classifier predict alone
        if (classifier != null) {
            val normalized = TextNormalizer.normalize(msg.text)
            val extracted = entityExtractor.extract(normalized)

            val modelTimingsMs = ArrayList<Double>()
            for (i in 0 until 100) {
                val nanos = measureNanoTime {
                    val featurized = featurizer.featurize(msg, normalized, extracted)
                    classifier!!.predict(featurized)
                }
                modelTimingsMs.add(nanos / 1_000_000.0)
            }
            modelTimingsMs.sort()
            val modelP95 = modelTimingsMs[(modelTimingsMs.size * 0.95).toInt()]
            // Budget for model inference: < 15 ms
            assertThat(modelP95).isLessThan(15.0)
        }
    }
}
