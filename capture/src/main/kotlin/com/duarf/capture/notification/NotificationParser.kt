package com.duarf.capture.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.duarf.capture.dedup.Deduplicator
import com.duarf.engine.extract.DltHeaderParser
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NotificationParser(
    private val installKey: ByteArray = "duarf-local-install-key".toByteArray(StandardCharsets.UTF_8)
) {

    // Phone number pattern: digits, spaces, +, -, brackets, 8+ digits (§5.2)
    private val phonePattern = Pattern.compile("""^[\s\d\+\-\(\)]{8,}$""")
    private val digitsOnlyPattern = Pattern.compile("""\d{8,}""")

    // Country code pattern: starts with + followed by 1 to 3 digits
    private val countryCodePattern = Pattern.compile("""^\+(\d{1,3})""")

    // File name pattern in notifications: e.g. document.pdf, image.png, update.apk
    private val fileNamePattern = Pattern.compile("""\b[\w\-. ]+\.(apk|pdf|doc|docx|xlsx|zip|rar)\b""", Pattern.CASE_INSENSITIVE)

    fun parse(sbn: StatusBarNotification): List<IncomingMessage> {
        val notification = sbn.notification ?: return emptyList()
        val extras = notification.extras ?: Bundle.EMPTY
        val packageName = sbn.packageName ?: ""

        val sourceApp = when (packageName) {
            "com.whatsapp" -> SourceApp.WHATSAPP
            "com.whatsapp.w4b" -> SourceApp.WHATSAPP_BUSINESS
            "com.google.android.apps.messaging" -> SourceApp.SMS_GOOGLE_MESSAGES
            "com.samsung.android.messaging" -> SourceApp.SMS_SAMSUNG_MESSAGES
            else -> SourceApp.UNKNOWN
        }

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val rawConversationId = notification.shortcutId
            ?: sbn.tag
            ?: conversationTitle
            ?: "unknown_chat"

        val conversationKey = computeHmacSha256(rawConversationId)

        // Try extracting MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            val isGroup = extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION, messagingStyle.isGroupConversation)
            val result = ArrayList<IncomingMessage>()

            for (m in messagingStyle.messages) {
                val person = m.person
                // Null or empty person indicates user's own message in Android MessagingStyle
                if (person == null || person.name.isNullOrEmpty()) {
                    continue
                }

                val (senderName, senderField) = when {
                    !person.name.isNullOrEmpty() -> Pair(person.name.toString(), "MessagingStyle.person.name")
                    extras.containsKey(Notification.EXTRA_CONVERSATION_TITLE) -> Pair(conversationTitle ?: "", "EXTRA_CONVERSATION_TITLE")
                    extras.containsKey(Notification.EXTRA_TITLE) -> Pair(conversationTitle ?: "", "EXTRA_TITLE")
                    else -> Pair(conversationTitle ?: "", "UNKNOWN")
                }
                debugSenderLogger?.invoke(packageName, senderField, senderName)

                val text = m.text?.toString() ?: ""
                if (text.isBlank()) continue

                val timestamp = if (m.timestamp > 0) m.timestamp else sbn.postTime
                val senderInfo = extractSenderInfo(senderName, sourceApp.isSms)
                val attachmentHint = extractAttachmentHint(text)
                val fingerprint = Deduplicator.computeFingerprint(conversationKey, senderName, text, timestamp)

                result.add(
                    IncomingMessage(
                        fingerprint = fingerprint,
                        source = SourceKind.NOTIFICATION,
                        app = sourceApp,
                        conversationKey = conversationKey,
                        senderDisplay = senderName,
                        senderKind = senderInfo.kind,
                        senderCountryCode = senderInfo.countryCode,
                        isGroup = isGroup,
                        text = text,
                        attachmentHint = attachmentHint,
                        receivedAtMillis = timestamp,
                        dltHeaderPrefix = senderInfo.dltPrefix,
                        dltHeaderBrand = senderInfo.dltBrand,
                        dltHeaderSuffix = senderInfo.dltSuffix
                    )
                )
            }

            if (result.isNotEmpty()) {
                return result
            }
        }

        // Fallback 1: EXTRA_BIG_TEXT or EXTRA_TEXT
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        if (text.isNotBlank()) {
            val isGroup = extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION, false)
            val senderField = "EXTRA_TITLE"
            debugSenderLogger?.invoke(packageName, senderField, title)

            val senderInfo = extractSenderInfo(title, sourceApp.isSms)
            val attachmentHint = extractAttachmentHint(text)
            val timestamp = sbn.postTime
            val fingerprint = Deduplicator.computeFingerprint(conversationKey, title, text, timestamp)

            return listOf(
                IncomingMessage(
                    fingerprint = fingerprint,
                    source = SourceKind.NOTIFICATION,
                    app = sourceApp,
                    conversationKey = conversationKey,
                    senderDisplay = title,
                    senderKind = senderInfo.kind,
                    senderCountryCode = senderInfo.countryCode,
                    isGroup = isGroup,
                    text = text,
                    attachmentHint = attachmentHint,
                    receivedAtMillis = timestamp,
                    dltHeaderPrefix = senderInfo.dltPrefix,
                    dltHeaderBrand = senderInfo.dltBrand,
                    dltHeaderSuffix = senderInfo.dltSuffix
                )
            )
        }

        // Fallback 2: EXTRA_TEXT_LINES
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) {
            val combinedText = lines.joinToString("\n") { it.toString() }
            val isGroup = extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION, false)
            val senderField = "EXTRA_TITLE"
            debugSenderLogger?.invoke(packageName, senderField, title)

            val senderInfo = extractSenderInfo(title, sourceApp.isSms)
            val attachmentHint = extractAttachmentHint(combinedText)
            val timestamp = sbn.postTime
            val fingerprint = Deduplicator.computeFingerprint(conversationKey, title, combinedText, timestamp)

            return listOf(
                IncomingMessage(
                    fingerprint = fingerprint,
                    source = SourceKind.NOTIFICATION,
                    app = sourceApp,
                    conversationKey = conversationKey,
                    senderDisplay = title,
                    senderKind = senderInfo.kind,
                    senderCountryCode = senderInfo.countryCode,
                    isGroup = isGroup,
                    text = combinedText,
                    attachmentHint = attachmentHint,
                    receivedAtMillis = timestamp,
                    dltHeaderPrefix = senderInfo.dltPrefix,
                    dltHeaderBrand = senderInfo.dltBrand,
                    dltHeaderSuffix = senderInfo.dltSuffix
                )
            )
        }

        return emptyList()
    }

    private data class SenderInfo(
        val kind: SenderKind,
        val countryCode: String?,
        val dltPrefix: String? = null,
        val dltBrand: String? = null,
        val dltSuffix: String? = null
    )

    private fun extractSenderInfo(display: String, isSms: Boolean): SenderInfo {
        return if (isSms) {
            val parsed = DltHeaderParser.parse(display)
            SenderInfo(
                kind = parsed.senderKind,
                countryCode = parsed.countryCode ?: "+91",
                dltPrefix = parsed.dltPrefix,
                dltBrand = parsed.dltBrand,
                dltSuffix = parsed.dltSuffix
            )
        } else {
            val (kind, cc) = deriveSenderKind(display)
            SenderInfo(
                kind = kind,
                countryCode = cc
            )
        }
    }

    private fun deriveSenderKind(display: String): Pair<SenderKind, String?> {
        val trimmed = display.trim()
        val digitsCount = trimmed.count { it.isDigit() }

        val isNumber = digitsCount >= 8 && phonePattern.matcher(trimmed).matches()
        if (isNumber) {
            val ccMatcher = countryCodePattern.matcher(trimmed)
            val countryCode = if (ccMatcher.find()) "+${ccMatcher.group(1)}" else "+91"
            return Pair(SenderKind.NUMBER_ONLY, countryCode)
        }

        return if (trimmed.isNotEmpty()) {
            Pair(SenderKind.NAMED, null)
        } else {
            Pair(SenderKind.UNKNOWN, null)
        }
    }

    private fun extractAttachmentHint(text: String): String? {
        val matcher = fileNamePattern.matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    private fun computeHmacSha256(value: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(installKey, "HmacSHA256")
            mac.init(keySpec)
            val bytes = mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            value.hashCode().toString()
        }
    }

    companion object {
        // Debug-only callback to log which field the sender was extracted from (§16.3, §19.1)
        @Volatile
        var debugSenderLogger: ((packageName: String, field: String, sender: String) -> Unit)? = null
    }
}
