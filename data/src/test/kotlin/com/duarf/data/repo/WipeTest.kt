package com.duarf.data.repo

import com.duarf.data.crypto.SoftwareCryptoEngine
import com.duarf.data.db.AlertEntity
import com.duarf.data.db.DailyCounterEntity
import com.duarf.data.db.SuppressedFingerprintEntity
import com.duarf.data.fakes.*
import com.duarf.data.prefs.UserPreferences
import com.duarf.engine.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException

class WipeTest {

    private lateinit var alertDao: FakeAlertDao
    private lateinit var suppressedDao: FakeSuppressedFingerprintDao
    private lateinit var conversationStatsDao: FakeConversationStatsDao
    private lateinit var dailyCounterDao: FakeDailyCounterDao
    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var crypto: SoftwareCryptoEngine
    private lateinit var alertRepository: AlertRepository
    private lateinit var statsRepository: StatsRepository

    @Before
    fun setUp() {
        alertDao = FakeAlertDao()
        suppressedDao = FakeSuppressedFingerprintDao()
        conversationStatsDao = FakeConversationStatsDao()
        dailyCounterDao = FakeDailyCounterDao()
        preferences = FakeUserPreferencesRepository()
        crypto = SoftwareCryptoEngine()
        alertRepository = AlertRepository(alertDao, suppressedDao, preferences, crypto)
        statsRepository = StatsRepository(conversationStatsDao, dailyCounterDao)
    }

    @Test
    fun `deleteAllData leaves empty tables, resets preferences, and wipes crypto keys`() {
        runBlocking {
            // 1. Populate tables
        val ciphertextBeforeWipe = crypto.encrypt("Sensitive message to be wiped")

        alertDao.insertAlert(
            AlertEntity(
                id = 1,
                fingerprint = "fp_123",
                createdAt = System.currentTimeMillis(),
                level = AlertLevel.DANGER.name,
                score = 0.99,
                category = ScamCategory.MALICIOUS_APK.name,
                sourceKind = SourceKind.NOTIFICATION.name,
                app = SourceApp.WHATSAPP.name,
                senderDisplayEncrypted = crypto.encrypt("+919999999999"),
                textEncrypted = ciphertextBeforeWipe,
                reasonsJsonEncrypted = crypto.encrypt("[]"),
                highlightsJson = "[]",
                engineVersion = "1.0.0"
            )
        )

        suppressedDao.insert(SuppressedFingerprintEntity("fp_suppressed", System.currentTimeMillis()))
        statsRepository.recordConversationMessage("conv_key_1")
        statsRepository.recordMessageChecked(AlertLevel.DANGER)

        preferences.updateRetentionDays(14)
        preferences.updateGroupAlerts(true)
        preferences.updateLanguageCode("hi")
        preferences.setOnboardingCompleted(true)

        // Verify pre-conditions
        assertThat(alertDao.getAllRawEntities()).isNotEmpty()
        assertThat(suppressedDao.isSuppressed("fp_suppressed")).isTrue()
        assertThat(statsRepository.getMessageCount("conv_key_1")).isEqualTo(1)
        assertThat(preferences.userPreferencesFlow.first().retentionDays).isEqualTo(14)
        assertThat(crypto.decrypt(ciphertextBeforeWipe)).isEqualTo("Sensitive message to be wiped")

        // 2. Perform Wipe (§12, §16.4)
        alertRepository.deleteAllData()
        statsRepository.deleteAll()

        // 3. Verify complete erasure
        // Empty tables
        assertThat(alertDao.getAllRawEntities()).isEmpty()
        assertThat(suppressedDao.isSuppressed("fp_suppressed")).isFalse()
        assertThat(statsRepository.getMessageCount("conv_key_1")).isEqualTo(0)
        assertThat(statsRepository.isConversationTrusted("conv_key_1")).isFalse()

        // Preferences reset to defaults
        val resetPrefs = preferences.userPreferencesFlow.first()
        assertThat(resetPrefs.retentionDays).isEqualTo(30)
        assertThat(resetPrefs.groupAlerts).isFalse()
        assertThat(resetPrefs.onboardingCompleted).isFalse()
        assertThat(resetPrefs.languageCode).isEqualTo("en")

        // KeyStore keys wiped: previous ciphertexts can never be decrypted
        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt(ciphertextBeforeWipe)
        }
        }
    }
}
