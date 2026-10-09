// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.repo

import com.duarf.data.db.ConversationStatsDao
import com.duarf.data.db.ConversationStatsEntity
import com.duarf.data.db.DailyCounterDao
import com.duarf.data.db.DailyCounterEntity
import com.duarf.engine.model.AlertLevel
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsRepository(
    private val conversationStatsDao: ConversationStatsDao,
    private val dailyCounterDao: DailyCounterDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val weeklyCounters: Flow<List<DailyCounterEntity>> = dailyCounterDao.getWeeklyCounters()

    suspend fun recordMessageChecked(level: AlertLevel) {
        val today = dateFormat.format(Date())
        val current = dailyCounterDao.getCounterForDay(today) ?: DailyCounterEntity(today, 0, 0, 0)

        val updated = current.copy(
            messagesChecked = current.messagesChecked + 1,
            cautions = if (level == AlertLevel.CAUTION) current.cautions + 1 else current.cautions,
            dangers = if (level == AlertLevel.DANGER) current.dangers + 1 else current.dangers
        )
        dailyCounterDao.upsert(updated)
    }

    suspend fun recordConversationMessage(conversationKey: String) {
        val now = System.currentTimeMillis()
        val current = conversationStatsDao.getStats(conversationKey)

        if (current == null) {
            conversationStatsDao.upsert(
                ConversationStatsEntity(
                    conversationKey = conversationKey,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    messageCount = 1,
                    trusted = false
                )
            )
        } else {
            conversationStatsDao.upsert(
                current.copy(
                    lastSeenAt = now,
                    messageCount = current.messageCount + 1
                )
            )
        }
    }

    suspend fun isConversationTrusted(conversationKey: String): Boolean {
        return conversationStatsDao.getStats(conversationKey)?.trusted ?: false
    }

    suspend fun setConversationTrusted(conversationKey: String, trusted: Boolean) {
        conversationStatsDao.setTrusted(conversationKey, trusted)
    }

    suspend fun getMessageCount(conversationKey: String): Int {
        return conversationStatsDao.getStats(conversationKey)?.messageCount ?: 0
    }

    suspend fun deleteAll() {
        conversationStatsDao.deleteAll()
        dailyCounterDao.deleteAll()
    }
}
