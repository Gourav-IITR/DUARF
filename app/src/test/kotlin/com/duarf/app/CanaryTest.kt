package com.duarf.app

import com.duarf.app.notification.AlertDispatcher
import com.duarf.app.service.MessageCoordinator
import com.duarf.data.crypto.SoftwareCryptoEngine
import com.duarf.data.fakes.*
import com.duarf.data.repo.AlertRepository
import com.duarf.data.repo.StatsRepository
import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class CanaryTest {

    private lateinit var engine: ScamEngine
    private lateinit var alertDao: FakeAlertDao
    private lateinit var suppressedDao: FakeSuppressedFingerprintDao
    private lateinit var conversationStatsDao: FakeConversationStatsDao
    private lateinit var dailyCounterDao: FakeDailyCounterDao
    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var crypto: SoftwareCryptoEngine
    private lateinit var alertRepository: AlertRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var testDispatcher: TestAlertDispatcher
    private lateinit var coordinator: MessageCoordinator

    class TestAlertDispatcher : AlertDispatcher {
        val dispatched = mutableListOf<Long>()

        override fun dispatchAlert(
            alertId: Long,
            fingerprint: String,
            senderDisplay: String?,
            verdict: Verdict,
            sourceApp: com.duarf.engine.model.SourceApp
        ) {
            dispatched.add(alertId)
        }
    }

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))

        alertDao = FakeAlertDao()
        suppressedDao = FakeSuppressedFingerprintDao()
        conversationStatsDao = FakeConversationStatsDao()
        dailyCounterDao = FakeDailyCounterDao()
        preferences = FakeUserPreferencesRepository()
        crypto = SoftwareCryptoEngine()

        alertRepository = AlertRepository(alertDao, suppressedDao, preferences, crypto)
        statsRepository = StatsRepository(conversationStatsDao, dailyCounterDao)
        testDispatcher = TestAlertDispatcher()

        coordinator = MessageCoordinator(
            engine = engine,
            alertRepository = alertRepository,
            statsRepository = statsRepository,
            preferences = preferences,
            notificationDispatcher = testDispatcher
        )
    }

    @Test
    fun `privacy canary test - 200 benign messages with canary strings never persist or leak`() {
        runBlocking {
            val canaryList = mutableListOf<String>()

        val benignTemplates = listOf(
            "Hey, are we still meeting for lunch tomorrow at Connaught Place?",
            "Happy birthday uncle! Wishing you great health and happiness always.",
            "Can you please share the notes from yesterday's mathematics lecture?",
            "The meeting has been rescheduled to 4 PM in the main conference room.",
            "Mom asked what time you will reach home for dinner tonight.",
            "Here is the photo from the trip last weekend, it turned out really nice.",
            "Please check the email I sent regarding the team presentation tomorrow.",
            "Good morning! Hope you have a productive and great week ahead.",
            "Did you finish reading the book you borrowed last month?",
            "Let's book the train tickets for Diwali vacation before the waitlist gets long."
        )

        // 1. Push 200 benign messages containing unique canary tokens through full pipeline
        for (i in 1..200) {
            val canary = "CANARY_BENIGN_TOKEN_${UUID.randomUUID()}_$i"
            canaryList.add(canary)

            val template = benignTemplates[i % benignTemplates.size]
            val text = "$template [$canary]"

            val msg = IncomingMessage(
                fingerprint = "fp_benign_$i",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv_${i % 10}",
                senderDisplay = "Friend $i",
                senderKind = SenderKind.NAMED,
                senderCountryCode = null,
                isGroup = false,
                text = text,
                attachmentHint = null,
                receivedAtMillis = System.currentTimeMillis() + i
            )

            coordinator.processMessage(msg)
        }

        // 2. Invariant 3 check: Zero benign messages persisted or dispatched
        assertThat(testDispatcher.dispatched).isEmpty()
        assertThat(alertDao.getAllRawEntities()).isEmpty()

        // 3. Canary scan: Assert that not a single canary string appears in any stored entity
        for (canary in canaryList) {
            val canaryBytes = canary.toByteArray(Charsets.UTF_8)
            val canaryRaw = String(canaryBytes, Charsets.ISO_8859_1)

            // Verify in all alert entities
            for (alert in alertDao.getAllRawEntities()) {
                val textRaw = alert.textEncrypted.let { String(it, Charsets.ISO_8859_1) }
                assertThat(textRaw.contains(canaryRaw)).isFalse()

                val senderRaw = alert.senderDisplayEncrypted?.let { String(it, Charsets.ISO_8859_1) } ?: ""
                assertThat(senderRaw.contains(canaryRaw)).isFalse()
            }

            // Verify in suppressed fingerprints
            for (fp in suppressedDao.getAll()) {
                assertThat(fp.contains(canary)).isFalse()
            }
        }
        }
    }

    @Test
    fun `privacy scam alert encryption test - scam messages are encrypted at rest with no plaintext leaks`() {
        runBlocking {
            val scamCanary = "CANARY_SCAM_TOKEN_${UUID.randomUUID()}"
            val senderPhone = "+919876543210"
            val scamText = "URGENT: SBI Electricity disconnection notice! Install app immediately http://sbi-bill.xyz/update.apk $scamCanary"

            val scamMsg = IncomingMessage(
                fingerprint = "fp_scam_1",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv_scam_1",
                senderDisplay = senderPhone,
                senderKind = SenderKind.NUMBER_ONLY,
                senderCountryCode = "+91",
                isGroup = false,
                text = scamText,
                attachmentHint = "update.apk",
                receivedAtMillis = System.currentTimeMillis()
            )

            coordinator.processMessage(scamMsg)

            // Scam alert was detected and dispatched
            assertThat(testDispatcher.dispatched).hasSize(1)

            val rawEntities = alertDao.getAllRawEntities()
            assertThat(rawEntities).hasSize(1)
            val entity = rawEntities.first()

            // Invariant 4 check: AES-256-GCM encryption at rest
            val canaryBytes = scamCanary.toByteArray(Charsets.UTF_8)
            val canaryRaw = String(canaryBytes, Charsets.ISO_8859_1)
            val storedTextRaw = String(entity.textEncrypted, Charsets.ISO_8859_1)
            val storedSenderRaw = String(entity.senderDisplayEncrypted!!, Charsets.ISO_8859_1)

            // Raw encrypted bytes NEVER contain the plaintext canary or phone number
            assertThat(storedTextRaw.contains(canaryRaw)).isFalse()
            assertThat(storedSenderRaw.contains(senderPhone)).isFalse()

            // Decryption using master key recovers exact text
            val decrypted = alertRepository.getAlertById(entity.id)
            assertThat(decrypted).isNotNull()
            assertThat(decrypted!!.text).isEqualTo(scamText)
            assertThat(decrypted.senderDisplay).isEqualTo(senderPhone)
        }
    }
}
