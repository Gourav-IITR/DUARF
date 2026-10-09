// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture.debug

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.duarf.capture.notification.NotificationDebugHook
import com.duarf.capture.notification.WaNotificationListener

/**
 * Debug-only Notification Recorder (§16, §19.1).
 * Writes sanitised extras of monitored notifications to app-private storage for retrieval over adb.
 * Off by default; compiled out of release builds.
 */
object NotificationRecorder : NotificationDebugHook {

    override fun onNotificationReceived(context: Context, sbn: StatusBarNotification) {
        record(context, sbn)
    }

    @Volatile
    private var lastSenderFieldByPkg = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    fun install() {
        WaNotificationListener.debugHook = this
        com.duarf.capture.notification.NotificationParser.debugSenderLogger = { pkg, field, sender ->
            lastSenderFieldByPkg[pkg] = Pair(field, sender)
        }
    }

    private const val PREFS_NAME = "duarf_debug_recorder"
    private const val KEY_ENABLED = "recorder_enabled"
    private const val RECORD_DIR_NAME = "recorded_notifications"

    @Volatile
    private var isEnabledOverride: Boolean? = null

    fun isEnabled(context: Context): Boolean {
        return isEnabledOverride ?: getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabledOverride = enabled
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun record(context: Context, sbn: StatusBarNotification): File? {
        if (!isEnabled(context)) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY

        val recordJson = JSONObject()
        recordJson.put("recordedAtMillis", System.currentTimeMillis())
        recordJson.put("recordedAtIso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
        recordJson.put("packageName", sbn.packageName)
        recordJson.put("id", sbn.id)
        recordJson.put("tag", sbn.tag ?: JSONObject.NULL)
        recordJson.put("postTime", sbn.postTime)
        recordJson.put("shortcutId", notification.shortcutId ?: JSONObject.NULL)

        val lastExtraction = lastSenderFieldByPkg[sbn.packageName]
        if (lastExtraction != null) {
            recordJson.put("parsedSenderField", lastExtraction.first)
            recordJson.put("parsedSender", lastExtraction.second)
        }

        // Extras summary
        val extrasJson = JSONObject()
        for (key in extras.keySet()) {
            val value = extras.get(key)
            when (value) {
                null -> extrasJson.put(key, JSONObject.NULL)
                is String, is Number, is Boolean -> extrasJson.put(key, value)
                is CharSequence -> extrasJson.put(key, value.toString())
                is Array<*> -> {
                    val arr = JSONArray()
                    value.forEach { arr.put(it?.toString() ?: "") }
                    extrasJson.put(key, arr)
                }
                is Bundle -> extrasJson.put(key, "[Bundle with ${value.keySet().size} keys]")
                else -> extrasJson.put(key, "[${value.javaClass.simpleName}]")
            }
        }
        recordJson.put("extras", extrasJson)

        // MessagingStyle messages
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (messagingStyle != null) {
            val msJson = JSONObject()
            msJson.put("conversationTitle", messagingStyle.conversationTitle ?: JSONObject.NULL)
            msJson.put("isGroupConversation", messagingStyle.isGroupConversation)

            val messagesArr = JSONArray()
            for (m in messagingStyle.messages) {
                val mJson = JSONObject()
                mJson.put("sender", m.person?.name ?: JSONObject.NULL)
                mJson.put("senderKey", m.person?.key ?: JSONObject.NULL)
                mJson.put("text", m.text ?: JSONObject.NULL)
                mJson.put("timestamp", m.timestamp)
                mJson.put("dataMimeType", m.dataMimeType ?: JSONObject.NULL)
                mJson.put("dataUri", m.dataUri?.toString() ?: JSONObject.NULL)
                messagesArr.put(mJson)
            }
            msJson.put("messages", messagesArr)
            recordJson.put("messagingStyle", msJson)
        }

        // Write to app-private directory
        val recordDir = File(context.filesDir, RECORD_DIR_NAME)
        if (!recordDir.exists()) {
            recordDir.mkdirs()
        }

        val fileName = "notif_${sbn.postTime}_${sbn.id}.json"
        val outFile = File(recordDir, fileName)
        outFile.writeText(recordJson.toString(2))
        return outFile
    }

    fun getRecordings(context: Context): List<File> {
        val recordDir = File(context.filesDir, RECORD_DIR_NAME)
        if (!recordDir.exists()) return emptyList()
        return recordDir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
    }

    fun clearRecordings(context: Context) {
        val recordDir = File(context.filesDir, RECORD_DIR_NAME)
        if (recordDir.exists()) {
            recordDir.deleteRecursively()
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
