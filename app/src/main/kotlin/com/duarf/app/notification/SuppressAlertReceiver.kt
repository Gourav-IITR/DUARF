// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duarf.data.repo.AlertRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SuppressAlertReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertRepository: AlertRepository

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getLongExtra(EXTRA_ALERT_ID, -1L)
        val fingerprint = intent.getStringExtra(EXTRA_FINGERPRINT)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        // Cancel the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        NotificationDispatcher.updateGroupSummary(context, notificationManager)

        if (fingerprint != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    alertRepository.suppressFingerprint(fingerprint)
                    if (alertId != -1L) {
                        alertRepository.dismissAlert(alertId)
                        alertRepository.updateFeedback(alertId, "SAFE")
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ALERT_ID = "EXTRA_ALERT_ID"
        const val EXTRA_FINGERPRINT = "EXTRA_FINGERPRINT"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }
}
