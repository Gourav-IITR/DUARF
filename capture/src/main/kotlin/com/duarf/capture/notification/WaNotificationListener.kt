// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duarf.capture.dedup.Deduplicator
import com.duarf.capture.log.SafeLog
import com.duarf.engine.model.IncomingMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListenerHealth(
    val isEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val lastEventMillis: Long = 0L,
    val lastConnectedMillis: Long = 0L
)

class WaNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Bounded channel: capacity 64, drop oldest (§5.2 step 5)
    private val notificationChannel = Channel<StatusBarNotification>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val parser = NotificationParser()
    private val deduplicator = Deduplicator()

    override fun onCreate() {
        super.onCreate()
        startProcessingPipeline()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _healthState.value = _healthState.value.copy(isConnected = false)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val now = System.currentTimeMillis()
        saveLastConnectedTimestamp(this, now)
        _healthState.value = _healthState.value.copy(
            isEnabled = true,
            isConnected = true,
            lastEventMillis = now,
            lastConnectedMillis = now
        )
        SafeLog.event(SafeLog.EventCode.LISTENER_CONNECTED)
        cancelProtectionPausedNotification(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _healthState.value = _healthState.value.copy(isConnected = false)
        SafeLog.event(SafeLog.EventCode.LISTENER_DISCONNECTED)
        // Request rebind (§5.2)
        requestRebind(ComponentName(this, WaNotificationListener::class.java))
        postProtectionPausedNotification()
    }

    /**
     * Strict main thread budget (§5.2, §15):
     * Package check and fast flag filter only, no parsing work on main thread.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // Step 1: Filter monitored packages
        val isMonitored = isMonitoredPackage(pkg)
        if (!isMonitored) return

        val notification = sbn.notification ?: return

        // Step 2: Skip group summaries, calls, ongoing notifications
        val flags = notification.flags
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val isCall = notification.category == Notification.CATEGORY_CALL
        val isOngoing = sbn.isOngoing

        if (isGroupSummary || isCall || isOngoing) {
            SafeLog.event(SafeLog.EventCode.NOTIFICATION_SKIPPED)
            return
        }

        // Step 5: Bounded channel hand-off to background worker
        val sendResult = notificationChannel.trySend(sbn)
        if (!sendResult.isSuccess) {
            SafeLog.event(SafeLog.EventCode.QUEUE_DROPPED_OLDEST)
        }
        SafeLog.event(SafeLog.EventCode.NOTIFICATION_RECEIVED)
    }

    private fun isMonitoredPackage(pkg: String): Boolean {
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            return true
        }
        if (checkSmsEnabled && (pkg == "com.google.android.apps.messaging" || pkg == "com.samsung.android.messaging")) {
            return true
        }
        // In debug builds, allow app's own package for Fake WhatsApp tests (§5.2)
        return pkg == packageName
    }

    private fun startProcessingPipeline() {
        // Single-threaded background consumer (§5.2 step 5)
        serviceScope.launch(Dispatchers.Default.limitedParallelism(1)) {
            for (sbn in notificationChannel) {
                try {
                    _healthState.value = _healthState.value.copy(lastEventMillis = System.currentTimeMillis())
                    debugHook?.onNotificationReceived(this@WaNotificationListener, sbn)

                    val messages = parser.parse(sbn)
                    for (msg in messages) {
                        // Deduplication (§5.4)
                        if (deduplicator.isDuplicateMessage(msg)) {
                            continue
                        }

                        val context = deduplicator.getContextAndRecord(msg)
                        SafeLog.event(SafeLog.EventCode.NOTIFICATION_PARSED)

                        // Notify listeners/subscribers of new incoming message
                        messageConsumer?.invoke(msg, context)
                    }
                } catch (_: Exception) {
                    SafeLog.event(SafeLog.EventCode.ERROR_DEGRADED)
                }
            }
        }
    }

    private fun postProtectionPausedNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_PROTECTION_STATUS,
                    "Protection Status",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about DUARF protection and listener service status"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val icon = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_alert

            val notification = NotificationCompat.Builder(this, CHANNEL_PROTECTION_STATUS)
                .setSmallIcon(icon)
                .setContentTitle("DUARF Protection Paused")
                .setContentText("Notification access was disconnected. Tap to re-enable protection.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Notification access was disconnected by the system. Tap to re-enable DUARF in Notification Access settings to stay protected.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID_PROTECTION_PAUSED, notification)
        } catch (_: Exception) {
            SafeLog.event(SafeLog.EventCode.ERROR_DEGRADED)
        }
    }

    companion object {
        const val NOTIFICATION_ID_PROTECTION_PAUSED = 19301
        const val CHANNEL_PROTECTION_STATUS = "duarf_protection_status"
        private const val PREFS_NAME = "duarf_listener_prefs"
        private const val KEY_LAST_CONNECTED_TIMESTAMP = "last_connected_timestamp"

        @Volatile
        var checkSmsEnabled: Boolean = true

        private val _healthState = MutableStateFlow(ListenerHealth())
        val healthState: StateFlow<ListenerHealth> = _healthState.asStateFlow()

        // Callback hook for debug recording (§16.3)
        @Volatile
        var debugHook: NotificationDebugHook? = null

        // Callback hook for engine consumption
        var messageConsumer: ((IncomingMessage, List<IncomingMessage>) -> Unit)? = null

        fun getLastConnectedTimestamp(context: Context): Long {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getLong(KEY_LAST_CONNECTED_TIMESTAMP, 0L)
            } catch (_: Exception) {
                0L
            }
        }

        fun saveLastConnectedTimestamp(context: Context, timestamp: Long) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CONNECTED_TIMESTAMP, timestamp).apply()
            } catch (_: Exception) {}
        }

        fun cancelProtectionPausedNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID_PROTECTION_PAUSED)
            } catch (_: Exception) {}
        }

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, WaNotificationListener::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }

        fun isListenerAccessGranted(context: Context): Boolean {
            return try {
                val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
                if (enabledPackages.contains(context.packageName)) {
                    true
                } else {
                    isNotificationServiceEnabled(context)
                }
            } catch (_: Exception) {
                isNotificationServiceEnabled(context)
            }
        }

        fun refreshHealth(context: Context) {
            val isEnabled = isListenerAccessGranted(context)
            val lastConnected = getLastConnectedTimestamp(context)
            val isCurrentlyConnected = _healthState.value.isConnected
            _healthState.value = _healthState.value.copy(
                isEnabled = isEnabled,
                isConnected = if (isEnabled) isCurrentlyConnected else false,
                lastConnectedMillis = lastConnected
            )
        }
    }
}
