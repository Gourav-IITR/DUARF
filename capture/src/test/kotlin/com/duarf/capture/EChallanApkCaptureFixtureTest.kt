package com.duarf.capture

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.ScamCategory
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Capture fixture for real-world e-challan APK scam:
 * Number-only sender (+919876543210), sends an image with text embedded inside,
 * followed by a document "RTO E challan.apk".
 *
 * NOTE: Fixture marked "structure unverified" until the NotificationRecorder
 * confirms WhatsApp's real on-device format (§19.1).
 */
class EChallanApkCaptureFixtureTest {

    private lateinit var engine: DefaultScamEngine

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val packs = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(packs)
    }

    @Test
    fun `e-challan document message evaluated with preceding image in context`() {
        val fixtureStatus = "structure unverified"
        assertThat(fixtureStatus).isEqualTo("structure unverified")

        val now = System.currentTimeMillis()
        val senderNumber = "+919876543210"

        // Step 1: Preceding image message stored as context (not scored by MVP since MVP does not OCR images)
        val imageContextMsg = IncomingMessage(
            fingerprint = "echallan-ctx-image-01",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-echallan-1",
            senderDisplay = senderNumber,
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "Photo",
            attachmentHint = null,
            receivedAtMillis = now - 15_000
        )

        // Step 2: Scored document message
        val docMsg = IncomingMessage(
            fingerprint = "echallan-doc-01",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-echallan-1",
            senderDisplay = senderNumber,
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = "RTO E challan.apk",
            attachmentHint = "application/vnd.android.package-archive",
            receivedAtMillis = now
        )

        val verdict = engine.analyze(docMsg, context = listOf(imageContextMsg))

        // Document message from unknown number with .apk file triggers DANGER
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.MALICIOUS_APK)
        assertThat(verdict.score).isAtLeast(0.85)

        val reasonIds = verdict.reasons.map { it.signalId }
        assertThat(reasonIds).contains("L01") // apk_file_or_link
        assertThat(reasonIds).contains("S01") // sender_number_only
    }
}
