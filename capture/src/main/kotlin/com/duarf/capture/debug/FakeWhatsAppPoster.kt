package com.duarf.capture.debug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

object FakeWhatsAppPoster {

    private const val CHANNEL_ID = "fake_whatsapp_channel"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Debug Fake WhatsApp",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun postFakeScamNotification(
        context: Context,
        sender: String = "+919876543210",
        messageText: String = "SBI Alert: Your account is blocked within 24 hours. Update KYC at http://sbi-kyc.xyz immediately.",
        isGroup: Boolean = false,
        notificationId: Int = 1001
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val senderPerson = Person.Builder()
            .setName(sender)
            .setKey(sender)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle(if (isGroup) "Family Group" else null)
            .setGroupConversation(isGroup)
            .addMessage(messageText, System.currentTimeMillis(), senderPerson)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    fun postFakeBenignNotification(
        context: Context,
        sender: String = "HDFCBK",
        messageText: String = "Your OTP for transaction of Rs 1500 is 492018. Do not share with anyone.",
        notificationId: Int = 1002
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val senderPerson = Person.Builder()
            .setName(sender)
            .setKey(sender)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .setGroupConversation(false)
            .addMessage(messageText, System.currentTimeMillis(), senderPerson)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }
}
