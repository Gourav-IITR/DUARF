package com.duarf.data.repo

import com.duarf.data.crypto.CryptoEngine
import com.duarf.data.db.AlertDao
import com.duarf.data.db.AlertEntity
import com.duarf.data.db.SuppressedFingerprintDao
import com.duarf.data.db.SuppressedFingerprintEntity
import com.duarf.data.log.SafeLog
import com.duarf.data.prefs.UserPreferencesRepository
import com.duarf.engine.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DecryptedAlert(
    val id: Long,
    val fingerprint: String,
    val createdAt: Long,
    val level: AlertLevel,
    val score: Double,
    val category: ScamCategory,
    val sourceKind: SourceKind,
    val app: SourceApp,
    val senderDisplay: String?,
    val text: String,
    val reasons: List<Reason>,
    val highlights: List<TextSpan>,
    val engineVersion: String,
    val userFeedback: String?,
    val dismissed: Boolean
)

class AlertRepository(
    private val alertDao: AlertDao,
    private val suppressedDao: SuppressedFingerprintDao,
    private val preferences: UserPreferencesRepository,
    private val crypto: CryptoEngine
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val activeAlerts: Flow<List<DecryptedAlert>> = alertDao.getActiveAlerts().map { list ->
        list.map { decryptAlert(it) }
    }

    val allAlerts: Flow<List<DecryptedAlert>> = alertDao.getAllAlerts().map { list ->
        list.map { decryptAlert(it) }
    }

    suspend fun saveAlert(
        message: IncomingMessage,
        verdict: Verdict
    ): Long {
        // Run retention purge on every alert insert (§12)
        val retentionDays = preferences.userPreferencesFlow.first().retentionDays
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        alertDao.purgeOlderThan(cutoff)

        // Encrypt sensitive fields (§12 Invariant 3 & 4)
        val encryptedSender = message.senderDisplay?.let { crypto.encrypt(it) }
        val encryptedText = crypto.encrypt(message.text)
        val encryptedReasons = crypto.encrypt(json.encodeToString(verdict.reasons))
        val highlightsStr = json.encodeToString(verdict.highlights)

        val entity = AlertEntity(
            fingerprint = message.fingerprint,
            createdAt = System.currentTimeMillis(),
            level = verdict.level.name,
            score = verdict.score,
            category = verdict.category.name,
            sourceKind = message.source.name,
            app = message.app.name,
            senderDisplayEncrypted = encryptedSender,
            textEncrypted = encryptedText,
            reasonsJsonEncrypted = encryptedReasons,
            highlightsJson = highlightsStr,
            engineVersion = verdict.engineVersion
        )

        val id = alertDao.insertAlert(entity)
        SafeLog.event(SafeLog.EventCode.DB_ALERT_SAVED)
        return id
    }

    suspend fun getAlertById(id: Long): DecryptedAlert? {
        val entity = alertDao.getAlertById(id) ?: return null
        return decryptAlert(entity)
    }

    suspend fun updateFeedback(alertId: Long, feedback: String) {
        alertDao.updateFeedback(alertId, feedback, System.currentTimeMillis())
    }

    suspend fun dismissAlert(alertId: Long) {
        alertDao.dismissAlert(alertId)
    }

    suspend fun suppressFingerprint(fingerprint: String) {
        suppressedDao.insert(SuppressedFingerprintEntity(fingerprint, System.currentTimeMillis()))
    }

    suspend fun isSuppressed(fingerprint: String): Boolean {
        return suppressedDao.isSuppressed(fingerprint)
    }

    suspend fun purgeExpired() {
        val retentionDays = preferences.userPreferencesFlow.first().retentionDays
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val purged = alertDao.purgeOlderThan(cutoff)
        SafeLog.event(SafeLog.EventCode.DB_RETENTION_PURGED, purged.toLong())
    }

    /**
     * "Delete all data" clears every table and both Keystore keys (§12)
     */
    suspend fun deleteAllData() {
        alertDao.deleteAllAlerts()
        suppressedDao.deleteAll()
        crypto.deleteAllKeys()
        preferences.clearAll()
        SafeLog.event(SafeLog.EventCode.DB_ALL_WIPED)
    }

    private fun decryptAlert(entity: AlertEntity): DecryptedAlert {
        val sender = entity.senderDisplayEncrypted?.let {
            try { crypto.decrypt(it) } catch (_: Exception) { null }
        }
        val text = try { crypto.decrypt(entity.textEncrypted) } catch (_: Exception) { "<encrypted>" }
        val reasons: List<Reason> = try {
            val reasonsStr = crypto.decrypt(entity.reasonsJsonEncrypted)
            json.decodeFromString(reasonsStr)
        } catch (_: Exception) {
            emptyList()
        }
        val highlights: List<TextSpan> = try {
            json.decodeFromString(entity.highlightsJson)
        } catch (_: Exception) {
            emptyList()
        }

        return DecryptedAlert(
            id = entity.id,
            fingerprint = entity.fingerprint,
            createdAt = entity.createdAt,
            level = try { AlertLevel.valueOf(entity.level) } catch (_: Exception) { AlertLevel.CAUTION },
            score = entity.score,
            category = try { ScamCategory.valueOf(entity.category) } catch (_: Exception) { ScamCategory.OTHER_SUSPICIOUS },
            sourceKind = try { SourceKind.valueOf(entity.sourceKind) } catch (_: Exception) { SourceKind.NOTIFICATION },
            app = try { SourceApp.valueOf(entity.app) } catch (_: Exception) { SourceApp.WHATSAPP },
            senderDisplay = sender,
            text = text,
            reasons = reasons,
            highlights = highlights,
            engineVersion = entity.engineVersion,
            userFeedback = entity.userFeedback,
            dismissed = entity.dismissed
        )
    }
}
