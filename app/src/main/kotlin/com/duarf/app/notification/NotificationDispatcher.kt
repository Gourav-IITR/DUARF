// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.duarf.app.MainActivity
import com.duarf.app.R
import com.duarf.app.locale.AppLanguage
import com.duarf.app.ui.theme.DuarfColors
import com.duarf.data.repo.FamilyContactRepository
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familyContacts: FamilyContactRepository
) : AlertDispatcher {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val localized = AppLanguage.wrap(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dangerChannel = NotificationChannel(
                CHANNEL_DANGER,
                localized.getString(R.string.channel_danger_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized.getString(R.string.channel_danger_desc)
                enableVibration(true)
            }

            val cautionChannel = NotificationChannel(
                CHANNEL_CAUTION,
                localized.getString(R.string.channel_caution_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localized.getString(R.string.channel_caution_desc)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(dangerChannel)
            notificationManager.createNotificationChannel(cautionChannel)
        }
    }

    override suspend fun dispatchAlert(
        alertId: Long,
        fingerprint: String,
        senderDisplay: String?,
        verdict: Verdict,
        sourceApp: SourceApp
    ) {
        if (verdict.level == AlertLevel.NONE) return

        // Text in the app's chosen language (English by default), not the phone's.
        val localized = AppLanguage.wrap(context)

        val isDanger = verdict.level == AlertLevel.DANGER
        val channelId = if (isDanger) CHANNEL_DANGER else CHANNEL_CAUTION
        val notificationId = (alertId % 100000).toInt() + 1000
        val accent = (if (isDanger) DuarfColors.Danger else DuarfColors.Caution).toArgb()

        val senderName = senderDisplay ?: localized.getString(R.string.unknown_sender)
        val reasonTitles = verdict.reasons.map { formatReason(localized, it) }
        val topReason = reasonTitles.firstOrNull() ?: localized.getString(R.string.alert_generic_summary)

        // Danger titles say what to do first ("Don't open the link — likely scam"), §13.2 as amended.
        val title = if (isDanger) {
            val action = localized.getString(WarningAction.forReasons(verdict.reasons).textRes)
            localized.getString(R.string.notif_danger_title, action)
        } else if (sourceApp.isSms) {
            localized.getString(R.string.alert_caution_sms_title, senderName)
        } else {
            localized.getString(R.string.alert_caution_title, senderName)
        }
        val text = if (isDanger) "$senderName · $topReason" else topReason

        // Expanded view: the sender, then up to three reasons, each once.
        val bullets = reasonTitles.take(3).joinToString("\n") { "• $it" }
        val bigText = if (isDanger) "$senderName\n$bullets" else bullets.ifEmpty { topReason }

        // Tapping a Danger warning opens the one-screen Stop page; a Caution opens the full detail.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_ALERT_ID, alertId)
            putExtra(EXTRA_NAV_STOP, isDanger)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
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

        // Locked phone: no sender or text, but the instruction still gets through.
        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_duarf)
            .setColor(accent)
            .setContentTitle(localized.getString(R.string.notif_public_title))
            .setContentText(localized.getString(R.string.notif_public_text))
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_duarf)
            .setColor(accent)
            .setLargeIcon(badge(if (isDanger) R.drawable.ic_notif_badge_danger else R.drawable.ic_notif_badge_caution))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (isDanger) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(GROUP_KEY)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(0, localized.getString(R.string.action_see_why), openPendingIntent)

        // "Call <name>" opens the dialer with the family contact's number and nothing else (invariant 9).
        familyContacts.current()?.let { contact ->
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val dialPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 70000,
                dialIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, localized.getString(R.string.action_call_contact, contact.name), dialPendingIntent)
        }

        builder.addAction(0, localized.getString(R.string.action_not_a_scam), suppressPendingIntent)

        notificationManager.notify(notificationId, builder.build())
        updateGroupSummary(context, notificationManager)
    }

    private fun badge(@DrawableRes res: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun formatReason(context: Context, reason: com.duarf.engine.model.Reason): String {
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
        const val EXTRA_NAV_STOP = "EXTRA_NAV_STOP"

        private const val GROUP_KEY = "com.duarf.app.ALERTS"
        private const val SUMMARY_ID = 999

        /** Groups two or more warnings under one summary (§13.2), and removes the summary when fewer remain. */
        fun updateGroupSummary(context: Context, notificationManager: NotificationManager) {
            val alerts = notificationManager.activeNotifications
                .filter { it.notification.group == GROUP_KEY && it.id != SUMMARY_ID }
            if (alerts.size < 2) {
                notificationManager.cancel(SUMMARY_ID)
                return
            }
            val localized = AppLanguage.wrap(context)
            val inbox = NotificationCompat.InboxStyle()
            alerts.take(5).forEach { sbn ->
                sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.let { inbox.addLine(it) }
            }
            val publicSummary = NotificationCompat.Builder(context, CHANNEL_DANGER)
                .setSmallIcon(R.drawable.ic_stat_duarf)
                .setColor(DuarfColors.Danger.toArgb())
                .setContentTitle(localized.getString(R.string.notif_public_title))
                .setContentText(localized.getString(R.string.notif_public_text))
                .build()
            val summary = NotificationCompat.Builder(context, CHANNEL_DANGER)
                .setSmallIcon(R.drawable.ic_stat_duarf)
                .setColor(DuarfColors.Danger.toArgb())
                .setContentTitle(localized.getString(R.string.channel_danger_name))
                .setStyle(inbox)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicSummary)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(SUMMARY_ID, summary)
        }
    }
}
