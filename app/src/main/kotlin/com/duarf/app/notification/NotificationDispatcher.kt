package com.duarf.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duarf.app.MainActivity
import com.duarf.app.R
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) : AlertDispatcher {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dangerChannel = NotificationChannel(
                CHANNEL_DANGER,
                context.getString(R.string.channel_danger_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_danger_desc)
                enableVibration(true)
            }

            val cautionChannel = NotificationChannel(
                CHANNEL_CAUTION,
                context.getString(R.string.channel_caution_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_caution_desc)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(dangerChannel)
            notificationManager.createNotificationChannel(cautionChannel)
        }
    }

    override fun dispatchAlert(
        alertId: Long,
        fingerprint: String,
        senderDisplay: String?,
        verdict: Verdict,
        sourceApp: SourceApp
    ) {
        if (verdict.level == AlertLevel.NONE) return

        val isDanger = verdict.level == AlertLevel.DANGER
        val channelId = if (isDanger) CHANNEL_DANGER else CHANNEL_CAUTION
        val notificationId = (alertId % 100000).toInt() + 1000

        val senderName = senderDisplay ?: context.getString(R.string.unknown_sender)
        val title = if (isDanger) {
            if (sourceApp.isSms) {
                context.getString(R.string.alert_danger_sms_title, senderName)
            } else {
                context.getString(R.string.alert_danger_title, senderName)
            }
        } else {
            if (sourceApp.isSms) {
                context.getString(R.string.alert_caution_sms_title, senderName)
            } else {
                context.getString(R.string.alert_caution_title, senderName)
            }
        }

        // Top reason
        val topReason = verdict.reasons.firstOrNull()
        val summaryText = topReason?.let { formatReason(it) } ?: context.getString(R.string.alert_generic_summary)

        // Expanded view: up to 3 reasons (§13.2)
        val bigText = buildString {
            append(summaryText)
            if (verdict.reasons.size > 1) {
                append("\n\n")
                verdict.reasons.take(3).forEach { r ->
                    append("• ").append(formatReason(r)).append("\n")
                }
            }
        }

        // Tap action: "See why" -> opens MainActivity with alert ID
        val detailIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_ALERT_ID, alertId)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: "Not a scam" -> SuppressAlertReceiver
        val suppressIntent = Intent(context, SuppressAlertReceiver::class.java).apply {
            putExtra(SuppressAlertReceiver.EXTRA_ALERT_ID, alertId)
            putExtra(SuppressAlertReceiver.EXTRA_FINGERPRINT, fingerprint)
            putExtra(SuppressAlertReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val suppressPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 50000,
            suppressIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (isDanger) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(detailPendingIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_see_why), detailPendingIntent)
            .addAction(0, context.getString(R.string.action_not_a_scam), suppressPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun formatReason(reason: com.duarf.engine.model.Reason): String {
        val resId = context.resources.getIdentifier(reason.titleKey, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            reason.signalId
        }
    }

    companion object {
        const val CHANNEL_DANGER = "scam_alerts_channel"
        const val CHANNEL_CAUTION = "cautions_channel"
        const val EXTRA_NAV_ALERT_ID = "EXTRA_NAV_ALERT_ID"
    }
}
