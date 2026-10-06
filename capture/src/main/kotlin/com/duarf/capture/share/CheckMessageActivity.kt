package com.duarf.capture.share

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import java.io.InputStream

/**
 * Entry point for checking messages shared from other apps or selected text (§5.3).
 * Receives:
 * - ACTION_SEND with MIME types:
 *     - text/plain
 *     - application/vnd.android.package-archive
 *     - application/octet-stream
 * - ACTION_PROCESS_TEXT with text/plain
 *
 * Security Invariant:
 * Inspects only file name and MIME type; NEVER opens input streams (openInputStream) or installs files.
 * MIME type is stored strictly in attachmentHint, never in text.
 */
class CheckMessageActivity : Activity() {

    data class SharePayload(
        val text: String,
        val attachmentHint: String?
    )

    interface ContentInspector {
        fun queryDisplayName(uri: Uri?): String?
        fun getType(uri: Uri?): String?
        fun openInputStream(uri: Uri?): InputStream?
    }

    class DefaultContentInspector(private val resolver: ContentResolver) : ContentInspector {
        override fun queryDisplayName(uri: Uri?): String? {
            if (uri == null) return null
            return try {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        override fun getType(uri: Uri?): String? = if (uri != null) resolver.getType(uri) else null

        override fun openInputStream(uri: Uri?): InputStream? = if (uri != null) resolver.openInputStream(uri) else null
    }

    interface SharedIntentData {
        val action: String?
        val type: String?
        fun getStringExtra(name: String): String?
        fun getCharSequenceExtra(name: String): CharSequence?
        fun getStreamUri(): Uri?
    }

    class AndroidSharedIntent(private val intent: Intent) : SharedIntentData {
        override val action: String? get() = intent.action
        override val type: String? get() = intent.type
        override fun getStringExtra(name: String): String? = intent.getStringExtra(name)
        override fun getCharSequenceExtra(name: String): CharSequence? = intent.getCharSequenceExtra(name)
        override fun getStreamUri(): Uri? {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                ?: intent.data
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val payload = extractSharePayload(intent, contentResolver)
        if (payload != null && payload.text.isNotBlank()) {
            val message = IncomingMessage(
                fingerprint = "share-${System.currentTimeMillis()}",
                source = SourceKind.SHARE,
                app = SourceApp.UNKNOWN,
                conversationKey = null,
                senderDisplay = null,
                senderKind = SenderKind.UNKNOWN,
                senderCountryCode = null,
                isGroup = false,
                text = payload.text,
                attachmentHint = payload.attachmentHint,
                receivedAtMillis = System.currentTimeMillis()
            )

            // Launch MainActivity with the message to display check result
            val mainIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_CHECK_TEXT", payload.text)
                putExtra("EXTRA_ATTACHMENT_HINT", payload.attachmentHint)
                putExtra("EXTRA_SOURCE", message.source.name)
            }
            if (mainIntent != null) {
                startActivity(mainIntent)
            }
        }

        finish()
    }

    companion object {
        const val MIME_TEXT_PLAIN = "text/plain"
        const val MIME_APK = "application/vnd.android.package-archive"
        const val MIME_OCTET_STREAM = "application/octet-stream"

        val ALLOWED_MIME_TYPES = setOf(
            MIME_TEXT_PLAIN,
            MIME_APK,
            MIME_OCTET_STREAM
        )

        fun extractSharePayload(intent: Intent?, contentResolver: ContentResolver?): SharePayload? {
            if (intent == null) return null
            return extractSharePayload(AndroidSharedIntent(intent), contentResolver?.let { DefaultContentInspector(it) })
        }

        fun extractSharePayload(data: SharedIntentData?, inspector: ContentInspector?): SharePayload? {
            if (data == null) return null

            when (data.action) {
                Intent.ACTION_SEND -> {
                    val rawType = data.type ?: ""
                    val mimeType = rawType.substringBefore(';').trim().lowercase()

                    if (mimeType !in ALLOWED_MIME_TYPES) {
                        return null
                    }

                    if (mimeType == MIME_TEXT_PLAIN) {
                        val text = data.getStringExtra(Intent.EXTRA_TEXT)
                        if (!text.isNullOrBlank()) {
                            return SharePayload(text = text, attachmentHint = null)
                        }
                    }

                    // For files (APK or octet-stream)
                    if (mimeType == MIME_APK || mimeType == MIME_OCTET_STREAM) {
                        val uri: Uri? = data.getStreamUri()
                        val fileName = (uri?.let { extractFileName(it, inspector) }
                            ?: inspector?.queryDisplayName(null))
                            ?: "attachment"

                        // Put MIME type only in attachmentHint, never in text (§5.3)
                        return SharePayload(
                            text = fileName,
                            attachmentHint = mimeType
                        )
                    }
                }
                Intent.ACTION_PROCESS_TEXT -> {
                    val rawType = data.type ?: ""
                    val mimeType = rawType.substringBefore(';').trim().lowercase()
                    if (mimeType == MIME_TEXT_PLAIN) {
                        val text = data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                        if (!text.isNullOrBlank()) {
                            return SharePayload(text = text, attachmentHint = null)
                        }
                    }
                }
            }
            return null
        }

        fun extractFileName(uri: Uri?, inspector: ContentInspector?): String? {
            if (uri == null) return null

            var fileName: String? = null
            if (inspector != null) {
                fileName = inspector.queryDisplayName(uri)
            }

            if (fileName.isNullOrBlank()) {
                fileName = uri.lastPathSegment
            }
            return fileName
        }
    }
}
