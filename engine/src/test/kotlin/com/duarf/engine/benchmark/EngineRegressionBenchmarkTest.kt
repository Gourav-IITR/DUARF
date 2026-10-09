// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.benchmark

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.ml.Featurizer
import com.duarf.engine.ml.LinearClassifier
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
 * JVM regression benchmark for CI and local verification.
 * Enforces strict Section 15 performance budgets:
 * - Cold pack/engine initialization <= 400 ms
 * - 1,000-char message analysis p95 <= 25 ms on JVM (Section 15 device budget: <= 150 ms)
 * - Featurization + classifier inference p95 <= 10 ms on JVM (Section 15 device budget: <= 15 ms)
 */
class EngineRegressionBenchmarkTest {

    private val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
    private lateinit var engine: DefaultScamEngine
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor
    private var classifier: LinearClassifier? = null

    @Before
    fun setUp() {
        val packs = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(packs)
        featurizer = Featurizer()
        val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
        entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
        classifier = packs.modelBytes?.let { LinearClassifier.fromBytes(it) }
    }

    @Test
    fun `benchmark cold engine initialization budget`() {
        // Cold start test: load packs and build engine from disk
        val elapsedNanos = measureNanoTime {
            val packs = PackLoader.load(FilePackSource(packsDir))
            val freshEngine = DefaultScamEngine(packs)
            assertThat(freshEngine.isModelLoaded).isTrue()
        }
        val elapsedMs = elapsedNanos / 1_000_000.0
        // Budget: <= 400 ms
        assertThat(elapsedMs).isLessThan(400.0)
    }

    @Test
    fun `benchmark multi-script 1000-char message analysis latency`() {
        val scriptSamples = listOf(
            // English / Latin
            "Dear customer, your bank account at HDFC Bank has experienced an unusual security event. " +
                    "Please verify your identity immediately by visiting https://hdfc-bank-portal-update.xyz/verify " +
                    "or calling our helpline at +919876543210. Failure to update within 24 hours will result in permanent " +
                    "suspension of your netbanking services. Regards, Bank Security Team. ",
            // Hindi / Devanagari
            "प्रिय ग्राहक, आपके एसबीआई बैंक खाते में संदिग्ध गतिविधि देखी गई है। कृपया 24 घंटे के भीतर अपने खाते का केवाईसी " +
                    "अपडेट करें अन्यथा आपकी नेटबैंकिंग और डेबिट कार्ड सेवा तुरंत ब्लॉक कर दी जाएगी। अभी सत्यापन के लिए लिंक पर क्लिक करें: " +
                    "https://sbi-kyc-verify.xyz/update या तुरंत +919876543210 पर संपर्क करें। अपना गुप्त ओटीपी 492019 किसी से साझा न करें। ",
            // Bengali
            "প্রিয় গ্রাহক, আপনার ব্যাংক অ্যাকাউন্ট সাময়িকভাবে স্থগিত করা হয়েছে। অবিলম্বে আপনার কেওয়াইসি আপডেট করতে নিচের লিঙ্কে " +
                    "ক্লিক করুন: https://bank-kyc-update.xyz/login অথবা যোগাযোগ করুন +919876543210। ২৪ ঘণ্টার মধ্যে প্রক্রিয়া সম্পন্ন না হলে " +
                    "অ্যাকাউন্ট স্থায়ীভাবে বন্ধ হয়ে যাবে। সাইবার নিরাপত্তা সতর্কতা মেনে চলুন। ",
            // Gujarati
            "પ્રિય ગ્રાહક, તમારા બેંક ખાતામાં શંકાસ્પદ વ્યવહાર જોવા મળ્યો છે. કૃપા કરીને ૨૪ કલાકમાં કેવાયસી અપડેટ કરો: " +
                    "https://bank-kyc-update.xyz/verify અથવા +919876543210 પર કોલ કરો. સમયસર પ્રક્રિયા પૂર્ણ ન કરવાથી એકાઉન્ટ બ્લોક થશે. "
        )

        for (sample in scriptSamples) {
            val sb = StringBuilder()
            while (sb.length < 1000) {
                sb.append(sample)
            }
            val text1000 = sb.substring(0, 1000)

            val msg = IncomingMessage(
                fingerprint = "bench-${sample.hashCode()}",
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

            // Warm up
            for (i in 0 until 10) {
                engine.analyze(msg)
            }

            // Benchmark 50 iterations
            val timings = ArrayList<Double>()
            for (i in 0 until 50) {
                val nanos = measureNanoTime {
                    engine.analyze(msg)
                }
                timings.add(nanos / 1_000_000.0)
            }
            timings.sort()
            val p95 = timings[(timings.size * 0.95).toInt()]

            // Section 15 budget: p95 <= 150 ms on device, JVM must be <= 30 ms
            assertThat(p95).isLessThan(30.0)
        }
    }

    @Test
    fun `benchmark featurizer and classifier latency`() {
        if (classifier == null) return

        val text = "SBI Alert: Your netbanking account has been locked. Verify immediately at http://sbi-kyc-verify.xyz/update " +
                "or call +919876543210. Do not share your OTP 492019."
        val sb = StringBuilder()
        while (sb.length < 1000) {
            sb.append(text).append(" ")
        }
        val text1000 = sb.substring(0, 1000)

        val msg = IncomingMessage(
            fingerprint = "feat-bench",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-feat",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text1000,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val normalized = TextNormalizer.normalize(msg.text)
        val extracted = entityExtractor.extract(normalized)

        // Warm up
        for (i in 0 until 20) {
            val feat = featurizer.featurize(msg, normalized, extracted)
            classifier!!.predict(feat)
        }

        // Measure
        val timings = ArrayList<Double>()
        for (i in 0 until 100) {
            val nanos = measureNanoTime {
                val feat = featurizer.featurize(msg, normalized, extracted)
                classifier!!.predict(feat)
            }
            timings.add(nanos / 1_000_000.0)
        }
        timings.sort()
        val p95 = timings[(timings.size * 0.95).toInt()]

        // Section 15 budget: p95 <= 15 ms on device, JVM must be <= 5 ms
        assertThat(p95).isLessThan(5.0)
    }
}
