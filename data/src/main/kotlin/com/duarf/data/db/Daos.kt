// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): AlertEntity?

    @Query("SELECT * FROM alerts WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getAlertByFingerprint(fingerprint: String): AlertEntity?

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE dismissed = 0 ORDER BY createdAt DESC")
    fun getActiveAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE level = :level ORDER BY createdAt DESC")
    fun getAlertsByLevel(level: String): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET userFeedback = :feedback, feedbackAt = :timestamp WHERE id = :id")
    suspend fun updateFeedback(id: Long, feedback: String, timestamp: Long)

    @Query("UPDATE alerts SET dismissed = 1 WHERE id = :id")
    suspend fun dismissAlert(id: Long)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)

    @Query("DELETE FROM alerts WHERE createdAt < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
}

@Dao
interface ConversationStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: ConversationStatsEntity)

    @Query("SELECT * FROM conversation_stats WHERE conversationKey = :key")
    suspend fun getStats(key: String): ConversationStatsEntity?

    @Query("UPDATE conversation_stats SET trusted = :trusted WHERE conversationKey = :key")
    suspend fun setTrusted(key: String, trusted: Boolean)

    @Query("DELETE FROM conversation_stats")
    suspend fun deleteAll()
}

@Dao
interface SuppressedFingerprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suppressed: SuppressedFingerprintEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM suppressed_fingerprints WHERE fingerprint = :fp)")
    suspend fun isSuppressed(fp: String): Boolean

    @Query("DELETE FROM suppressed_fingerprints")
    suspend fun deleteAll()
}

@Dao
interface DailyCounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(counter: DailyCounterEntity)

    @Query("SELECT * FROM daily_counters WHERE day = :day")
    suspend fun getCounterForDay(day: String): DailyCounterEntity?

    @Query("SELECT * FROM daily_counters ORDER BY day DESC LIMIT 7")
    fun getWeeklyCounters(): Flow<List<DailyCounterEntity>>

    @Query("DELETE FROM daily_counters")
    suspend fun deleteAll()
}
