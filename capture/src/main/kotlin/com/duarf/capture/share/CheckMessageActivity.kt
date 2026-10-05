package com.duarf.capture.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind

/**
 * Entry point for checking messages shared from other apps or selected text (§5.3).
 * Receives ACTION_SEND (text/plain) or ACTION_PROCESS_TEXT.
 */
class CheckMessageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textToCheck = extractText(intent)
        if (!textToCheck.isNullOrBlank()) {
            val message = IncomingMessage(
                fingerprint = "share-${System.currentTimeMillis()}",
                source = if (intent.action == Intent.ACTION_PROCESS_TEXT) SourceKind.SHARE else SourceKind.SHARE,
                app = SourceApp.UNKNOWN,
                conversationKey = null,
                senderDisplay = null,
                senderKind = SenderKind.UNKNOWN,
                senderCountryCode = null,
                isGroup = false,
                text = textToCheck,
                attachmentHint = null,
                receivedAtMillis = System.currentTimeMillis()
            )

            // Launch MainActivity with the message to display check result
            val mainIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_CHECK_TEXT", textToCheck)
                putExtra("EXTRA_SOURCE", message.source.name)
            }
            if (mainIntent != null) {
                startActivity(mainIntent)
            }
        }

        finish()
    }

    private fun extractText(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            Intent.ACTION_PROCESS_TEXT -> {
                if (intent.type == "text/plain") {
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                } else null
            }
            else -> null
        }
    }
}
