// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.fakes

import com.duarf.data.db.*
import com.duarf.data.prefs.UserPreferences
import com.duarf.data.prefs.UserPreferencesRepository
import com.duarf.engine.model.Sensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeAlertDao : AlertDao {
    private val alerts = mutableMapOf<Long, AlertEntity>()
    private val alertsFlow = MutableStateFlow<List<AlertEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        alertsFlow.value = alerts.values.sortedByDescending { it.createdAt }
    }

    override suspend fun insertAlert(alert: AlertEntity): Long {
        val id = if (alert.id == 0L) nextId++ else alert.id
        val entity = alert.copy(id = id)
        alerts[id] = entity
        emit()
        return id
    }

    override suspend fun getAlertById(id: Long): AlertEntity? = alerts[id]

    override suspend fun getAlertByFingerprint(fingerprint: String): AlertEntity? =
        alerts.values.firstOrNull { it.fingerprint == fingerprint }

    override fun getAllAlerts(): Flow<List<AlertEntity>> = alertsFlow

    override fun getActiveAlerts(): Flow<List<AlertEntity>> =
        alertsFlow.map { list -> list.filter { !it.dismissed } }

    override fun getAlertsByLevel(level: String): Flow<List<AlertEntity>> =
        alertsFlow.map { list -> list.filter { it.level == level } }

    override suspend fun updateFeedback(id: Long, feedback: String, timestamp: Long) {
        val current = alerts[id] ?: return
        alerts[id] = current.copy(userFeedback = feedback, feedbackAt = timestamp)
        emit()
    }

    override suspend fun dismissAlert(id: Long) {
        val current = alerts[id] ?: return
        alerts[id] = current.copy(dismissed = true)
        emit()
    }

    override suspend fun deleteAlert(id: Long) {
        alerts.remove(id)
        emit()
    }

    override suspend fun purgeOlderThan(cutoffMillis: Long): Int {
        val toRemove = alerts.filter { it.value.createdAt < cutoffMillis }.keys
        toRemove.forEach { alerts.remove(it) }
        if (toRemove.isNotEmpty()) emit()
        return toRemove.size
    }

    override suspend fun deleteAllAlerts() {
        alerts.clear()
        emit()
    }

    fun getAllRawEntities(): List<AlertEntity> = alerts.values.toList()
}

class FakeSuppressedFingerprintDao : SuppressedFingerprintDao {
    private val suppressed = mutableSetOf<String>()

    override suspend fun insert(suppressed: SuppressedFingerprintEntity) {
        this.suppressed.add(suppressed.fingerprint)
    }

    override suspend fun isSuppressed(fp: String): Boolean = fp in suppressed

    override suspend fun deleteAll() {
        suppressed.clear()
    }

    fun getAll(): Set<String> = suppressed.toSet()
}

class FakeConversationStatsDao : ConversationStatsDao {
    private val stats = mutableMapOf<String, ConversationStatsEntity>()

    override suspend fun upsert(stats: ConversationStatsEntity) {
        this.stats[stats.conversationKey] = stats
    }

    override suspend fun getStats(key: String): ConversationStatsEntity? = stats[key]

    override suspend fun setTrusted(key: String, trusted: Boolean) {
        val current = stats[key] ?: return
        stats[key] = current.copy(trusted = trusted)
    }

    override suspend fun deleteAll() {
        stats.clear()
    }
}

class FakeDailyCounterDao : DailyCounterDao {
    private val counters = mutableMapOf<String, DailyCounterEntity>()
    private val flow = MutableStateFlow<List<DailyCounterEntity>>(emptyList())

    override suspend fun upsert(counter: DailyCounterEntity) {
        counters[counter.day] = counter
        flow.value = counters.values.sortedByDescending { it.day }.take(7)
    }

    override suspend fun getCounterForDay(day: String): DailyCounterEntity? = counters[day]

    override fun getWeeklyCounters(): Flow<List<DailyCounterEntity>> = flow

    override suspend fun deleteAll() {
        counters.clear()
        flow.value = emptyList()
    }
}

class FakeUserPreferencesRepository(
    initialPrefs: UserPreferences = UserPreferences()
) : UserPreferencesRepository {
    private val _prefs = MutableStateFlow(initialPrefs)
    override val userPreferencesFlow: Flow<UserPreferences> = _prefs

    override suspend fun updateSensitivity(sensitivity: Sensitivity) {
        _prefs.update { it.copy(sensitivity = sensitivity) }
    }

    override suspend fun updateMonitoredApps(apps: Set<String>) {
        _prefs.update { it.copy(monitoredApps = apps) }
    }

    override suspend fun updateCheckSms(enabled: Boolean) {
        _prefs.update { it.copy(checkSms = enabled) }
    }

    override suspend fun updateGroupAlerts(enabled: Boolean) {
        _prefs.update { it.copy(groupAlerts = enabled) }
    }

    override suspend fun updateRetentionDays(days: Int) {
        _prefs.update { it.copy(retentionDays = days) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _prefs.update { it.copy(onboardingCompleted = completed) }
    }

    override suspend fun updateLanguageCode(langCode: String) {
        _prefs.update { it.copy(languageCode = langCode) }
    }

    override suspend fun updateFamilyContactCipher(cipher: String?) {
        _prefs.update { it.copy(familyContactCipher = cipher) }
    }

    override suspend fun clearAll() {
        _prefs.value = UserPreferences()
    }
}
