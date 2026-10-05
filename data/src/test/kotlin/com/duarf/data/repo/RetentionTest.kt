package com.duarf.data.repo

import com.duarf.data.crypto.SoftwareCryptoEngine
import com.duarf.data.db.AlertEntity
import com.duarf.data.fakes.FakeAlertDao
import com.duarf.data.fakes.FakeSuppressedFingerprintDao
import com.duarf.data.fakes.FakeUserPreferencesRepository
import com.duarf.data.prefs.UserPreferences
import com.duarf.engine.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class RetentionTest {

    private lateinit var alertDao: FakeAlertDao
    private lateinit var suppressedDao: FakeSuppressedFingerprintDao
    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var crypto: SoftwareCryptoEngine
    private lateinit var repository: AlertRepository

    @Before
    fun setUp() {
        alertDao = FakeAlertDao()
        suppressedDao = FakeSuppressedFingerprintDao()
        preferences = FakeUserPreferencesRepository(UserPreferences(retentionDays = 30))
        crypto = SoftwareCryptoEngine()
        repository = AlertRepository(alertDao, suppressedDao, preferences, crypto)
    }

    private fun createRawAlertEntity(
        id: Long,
        createdAt: Long,
        text: String = "Test scam alert"
    ): AlertEntity {
        return AlertEntity(
            id = id,
            fingerprint = "fp_$id",
            createdAt = createdAt,
            level = AlertLevel.DANGER.name,
            score = 0.95,
            category = ScamCategory.PHISHING_BANK_KYC.name,
            sourceKind = SourceKind.NOTIFICATION.name,
            app = SourceApp.WHATSAPP.name,
            senderDisplayEncrypted = crypto.encrypt("+919876543210"),
            textEncrypted = crypto.encrypt(text),
            reasonsJsonEncrypted = crypto.encrypt("[]"),
            highlightsJson = "[]",
            engineVersion = "1.0.0"
        )
    }

    @Test
    fun `alerts older than 30 days are purged upon purgeExpired`() {
        runBlocking {
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            // Alert from 35 days ago (should be purged)
            alertDao.insertAlert(createRawAlertEntity(id = 1, createdAt = now - (35 * oneDayMillis)))
            // Alert from 15 days ago (should be kept)
            alertDao.insertAlert(createRawAlertEntity(id = 2, createdAt = now - (15 * oneDayMillis)))
            // Alert from 1 hour ago (should be kept)
            alertDao.insertAlert(createRawAlertEntity(id = 3, createdAt = now - 3600_000L))

            assertThat(alertDao.getAllRawEntities()).hasSize(3)

            // Trigger retention purge
            repository.purgeExpired()

            val remaining = alertDao.getAllRawEntities()
            assertThat(remaining).hasSize(2)
            assertThat(remaining.map { it.id }).containsExactly(2L, 3L)
        }
    }

    @Test
    fun `alerts respect custom retention days setting`() {
        runBlocking {
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            // Set retention to 7 days
            preferences.updateRetentionDays(7)

            // Alert from 10 days ago (should be purged)
            alertDao.insertAlert(createRawAlertEntity(id = 1, createdAt = now - (10 * oneDayMillis)))
            // Alert from 3 days ago (should be kept)
            alertDao.insertAlert(createRawAlertEntity(id = 2, createdAt = now - (3 * oneDayMillis)))

            repository.purgeExpired()

            val remaining = alertDao.getAllRawEntities()
            assertThat(remaining).hasSize(1)
            assertThat(remaining.first().id).isEqualTo(2L)
        }
    }

    @Test
    fun `saveAlert automatically triggers retention purge`() {
        runBlocking {
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            // Insert stale alert from 40 days ago directly into DB
            alertDao.insertAlert(createRawAlertEntity(id = 99, createdAt = now - (40 * oneDayMillis)))
            assertThat(alertDao.getAllRawEntities()).hasSize(1)

            // Save a new alert via repository
            val msg = IncomingMessage(
                fingerprint = "fp_new",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv_1",
                senderDisplay = "+919876543210",
                senderKind = SenderKind.NUMBER_ONLY,
                senderCountryCode = "+91",
                isGroup = false,
                text = "Your bank is blocked click http://fake.com",
                attachmentHint = null,
                receivedAtMillis = now
            )
            val verdict = Verdict(
                level = AlertLevel.DANGER,
                score = 0.92,
                ruleScore = 0.92,
                modelProbability = null,
                category = ScamCategory.PHISHING_BANK_KYC,
                reasons = emptyList(),
                highlights = emptyList(),
                engineVersion = "1.0.0"
            )

            val newId = repository.saveAlert(msg, verdict)

            // The stale alert should have been purged during saveAlert
            val remaining = alertDao.getAllRawEntities()
            assertThat(remaining).hasSize(1)
            assertThat(remaining.first().id).isEqualTo(newId)
        }
    }
}
