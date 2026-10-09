// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.service

import com.duarf.app.notification.AlertDispatcher
import com.duarf.capture.notification.WaNotificationListener
import com.duarf.data.prefs.UserPreferencesRepository
import com.duarf.data.repo.AlertRepository
import com.duarf.data.repo.StatsRepository
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.ScamEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageCoordinator @Inject constructor(
    private val engine: ScamEngine,
    private val alertRepository: AlertRepository,
    private val statsRepository: StatsRepository,
    private val preferences: UserPreferencesRepository,
    private val notificationDispatcher: AlertDispatcher
) {

    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize() {
        coordinatorScope.launch {
            preferences.userPreferencesFlow.collect { prefs ->
                WaNotificationListener.checkSmsEnabled = prefs.checkSms
            }
        }
        WaNotificationListener.messageConsumer = { message, context ->
            coordinatorScope.launch {
                processMessage(message, context)
            }
        }
    }

    suspend fun processMessage(
        message: IncomingMessage,
        context: List<IncomingMessage> = emptyList()
    ) {
        // 1. Check if suppressed (§5.4, §12)
        if (alertRepository.isSuppressed(message.fingerprint)) {
            return
        }

        val prefs = preferences.userPreferencesFlow.first()

        // 2. Check if SMS check is disabled for SMS messages
        if (message.app.isSms && !prefs.checkSms) {
            return
        }

        // 3. Check group alerts setting (§12)
        if (message.isGroup && !prefs.groupAlerts) {
            return
        }

        // 4. Record conversation message in stats
        if (message.conversationKey != null) {
            statsRepository.recordConversationMessage(message.conversationKey!!)
        }

        // 5. Run detection engine (§6, §10)
        val verdict = engine.analyze(
            message = message,
            context = context,
            sensitivity = prefs.sensitivity
        )

        // 6. Update daily stats
        statsRepository.recordMessageChecked(verdict.level)

        // 7. Save alert and dispatch notification for CAUTION and DANGER
        if (verdict.level != AlertLevel.NONE) {
            val alertId = alertRepository.saveAlert(message, verdict)
            notificationDispatcher.dispatchAlert(
                alertId = alertId,
                fingerprint = message.fingerprint,
                senderDisplay = message.senderDisplay,
                verdict = verdict,
                sourceApp = message.app
            )
        }
    }
}
