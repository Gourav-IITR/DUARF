package com.duarf.capture.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
    val lastEventMillis: Long = 0L
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
        _healthState.value = _healthState.value.copy(
            isEnabled = true,
            isConnected = true,
            lastEventMillis = System.currentTimeMillis()
        )
        SafeLog.event(SafeLog.EventCode.LISTENER_CONNECTED)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _healthState.value = _healthState.value.copy(isConnected = false)
        SafeLog.event(SafeLog.EventCode.LISTENER_DISCONNECTED)
        // Request rebind (§5.2)
        requestRebind(ComponentName(this, WaNotificationListener::class.java))
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
        // In debug builds, allow app's own package for Fake WhatsApp tests (§5.2)
        return pkg == packageName
    }

    private fun startProcessingPipeline() {
        // Single-threaded background consumer (§5.2 step 5)
        serviceScope.launch(Dispatchers.Default.limitedParallelism(1)) {
            for (sbn in notificationChannel) {
                try {
                    _healthState.value = _healthState.value.copy(lastEventMillis = System.currentTimeMillis())

                    val messages = parser.parse(sbn)
                    for (msg in messages) {
                        // Deduplication (§5.4)
                        if (deduplicator.isDuplicate(msg.fingerprint)) {
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

    companion object {
        private val _healthState = MutableStateFlow(ListenerHealth())
        val healthState: StateFlow<ListenerHealth> = _healthState.asStateFlow()

        // Callback hook for engine consumption
        var messageConsumer: ((IncomingMessage, List<IncomingMessage>) -> Unit)? = null

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, WaNotificationListener::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }
    }
}
