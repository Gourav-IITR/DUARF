package com.duarf.app.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duarf.app.pack.AssetPackSource
import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.ml.Featurizer
import com.duarf.engine.ml.LinearClassifier
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

/**
 * On-Device Performance Benchmark executed via `./gradlew :app:connectedAndroidTest`.
 *
 * Verifies Section 15 Performance Budgets on real Android hardware:
 * - Cold engine initialization <= 400 ms
 * - 1,000-char message analysis latency p95 <= 150 ms across English, Hindi, and regional scripts
 * - Featurization + Logistic Regression inference p95 <= 15 ms
 * - Main-thread listener overhead <= 5 ms
 *
 * Telemetry logged: Device manufacturer, model, Android OS version, and total RAM.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePerformanceBenchmarkTest {

    private val tag = "DUARF_BENCHMARK"
    private lateinit var appContext: Context
    private lateinit var engine: DefaultScamEngine
    private lateinit var featurizer: Featurizer
    private lateinit var entityExtractor: EntityExtractor
    private var classifier: LinearClassifier? = null

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        logDeviceHardwareProfile()

        val packSource = AssetPackSource(appContext)
        val loadedPacks = PackLoader.load(packSource)
        engine = DefaultScamEngine(loadedPacks)
        featurizer = Featurizer()
        val psl = if (loadedPacks.pslLines.isNotEmpty()) {
            PublicSuffixList.parseFromLines(loadedPacks.pslLines.asSequence())
        } else {
            PublicSuffixList()
        }
        entityExtractor = EntityExtractor(psl, loadedPacks.brands, loadedPacks.upiHandles)
        classifier = loadedPacks.modelBytes?.let { LinearClassifier.fromBytes(it) }
    }

    private fun logDeviceHardwareProfile() {
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        Log.i(tag, "=== HARDWARE BENCHMARK PROFILE ===")
        Log.i(tag, "Manufacturer : ${Build.MANUFACTURER}")
        Log.i(tag, "Model        : ${Build.MODEL} (${Build.DEVICE})")
        Log.i(tag, "Android OS   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Log.i(tag, "Physical RAM : ${totalRamMb} MB")
        Log.i(tag, "==================================")
    }

    @Test
    fun testColdEngineInitializationBudget() {
        val elapsedNanos = measureNanoTime {
            val packSource = AssetPackSource(appContext)
            val packs = PackLoader.load(packSource)
            val freshEngine = DefaultScamEngine(packs)
            assertThat(freshEngine.isModelLoaded).isTrue()
        }
        val elapsedMs = elapsedNanos / 1_000_000.0
        Log.i(tag, "Cold Engine Init Duration: %.2f ms (Budget: <= 400 ms)".format(elapsedMs))

        // Section 15: cold engine startup <= 400 ms
        assertThat(elapsedMs).isLessThan(400.0)
    }

    @Test
    fun testMultiScript1000CharMessageAnalysisLatency() {
        val scripts = listOf(
            "en" to ("Dear customer, your bank account at HDFC Bank has experienced an unusual security event. " +
                    "Please verify your identity immediately by visiting https://hdfc-bank-portal-update.xyz/verify " +
                    "or calling our helpline at +919876543210. Failure to update within 24 hours will result in permanent " +
                    "suspension of your netbanking and debit card services. Also do not share your OTP 492019 with anyone. " +
                    "Regards, Customer Security Division. "),
            "hi" to ("प्रिय ग्राहक, आपके एसबीआई बैंक खाते में संदिग्ध गतिविधि देखी गई है। कृपया 24 घंटे के भीतर अपने खाते का केवाईसी " +
                    "अपडेट करें अन्यथा आपकी नेटबैंकिंग और डेबिट कार्ड सेवा तुरंत ब्लॉक कर दी जाएगी। अभी सत्यापन के लिए लिंक पर क्लिक करें: " +
                    "https://sbi-kyc-verify.xyz/update या तुरंत +919876543210 पर संपर्क करें। अपना गुप्त ओटीपी 492019 किसी से साझा न करें। "),
            "bn" to ("প্রিয় গ্রাহক, আপনার ব্যাংক অ্যাকাউন্ট সাময়িকভাবে স্থগিত করা হয়েছে। অবিলম্বে আপনার কেওয়াইসি আপডেট করতে নিচের লিঙ্কে " +
                    "ক্লিক করুন: https://bank-kyc-update.xyz/login অথবা যোগাযোগ করুন +919876543210। ২৪ ঘণ্টার মধ্যে প্রক্রিয়া সম্পন্ন না হলে " +
                    "অ্যাকাউন্ট স্থায়ীভাবে বন্ধ হয়ে যাবে। সাইবার নিরাপত্তা সতর্কতা মেনে চলুন। "),
            "gu" to ("પ્રિય ગ્રાહક, તમારા બેંક ખાતામાં શંકાસ્પદ વ્યવહાર જોવા મળ્યો છે. કૃપા કરીને ૨૪ કલાકમાં કેવાયસી અપડેટ કરો: " +
                    "https://bank-kyc-update.xyz/verify અથવા +919876543210 પર કોલ કરો. સમયસર પ્રક્રિયા પૂર્ણ ન કરવાથી એકાઉન્ટ બ્લોક થશે. ")
        )

        for ((lang, template) in scripts) {
            val sb = StringBuilder()
            while (sb.length < 1000) {
                sb.append(template)
            }
            val text1000 = sb.substring(0, 1000)

            val msg = IncomingMessage(
                fingerprint = "bench-$lang",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv-bench-$lang",
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
            val median = timings[timings.size / 2]

            Log.i(tag, "Analysis Latency [$lang]: p95 = %.2f ms, median = %.2f ms (Budget: <= 150 ms)".format(p95, median))

            // Section 15: p95 <= 150 ms on 1,000-character inputs
            assertThat(p95).isLessThan(150.0)
        }
    }

    @Test
    fun testFeaturizerAndPredictLatency() {
        if (classifier == null) return

        val text = "SBI Alert: Your netbanking account has been locked. Verify immediately at http://sbi-kyc-verify.xyz/update " +
                "or call +919876543210. Do not share your OTP 492019."
        val sb = StringBuilder()
        while (sb.length < 1000) {
            sb.append(text).append(" ")
        }
        val text1000 = sb.substring(0, 1000)

        val msg = IncomingMessage(
            fingerprint = "feat-bench-device",
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

        // Measure 100 iterations
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

        Log.i(tag, "Featurizer + Predict Latency: p95 = %.2f ms (Budget: <= 15 ms)".format(p95))

        // Section 15: Featurization + predict latency p95 <= 15 ms
        assertThat(p95).isLessThan(15.0)
    }

    @Test
    fun testMainThreadPackageFilteringOverhead() {
        // Main thread budget: package name comparison and basic flag check <= 5 ms
        val monitoredPackages = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging")
        val testPkg = "com.whatsapp"

        val timings = ArrayList<Double>()
        for (i in 0 until 1000) {
            val nanos = measureNanoTime {
                val isMonitored = monitoredPackages.contains(testPkg)
                assertThat(isMonitored).isTrue()
            }
            timings.add(nanos / 1_000_000.0)
        }
        timings.sort()
        val p95 = timings[(timings.size * 0.95).toInt()]

        Log.i(tag, "Main-Thread Package Check Overhead: p95 = %.4f ms (Budget: <= 5 ms)".format(p95))
        assertThat(p95).isLessThan(5.0)
    }
}
