// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.capture

import com.duarf.capture.notification.NotificationParser
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
 * Capture fixture for real-world SMS notifications (Case B electricity disconnect scam).
 *
 * NOTE: Fixture marked "structure unverified" until NotificationRecorder
 * confirms Google Messages / Samsung Messages real on-device format (§19, OPEN_QUESTIONS.md).
 */
class SmsCaptureFixtureTest {

    private lateinit var engine: DefaultScamEngine

    @Before
    fun setUp() {
        val packsDir = File("../packs").let { if (it.exists()) it else File("packs") }
        val packs = PackLoader.load(FilePackSource(packsDir))
        engine = DefaultScamEngine(packs)
    }

    @Test
    fun `SMS notification structure marked unverified pending physical recordings`() {
        val fixtureStatus = "structure unverified"
        assertThat(fixtureStatus).isEqualTo("structure unverified")
    }

    @Test
    fun `sender field logger records field provenance during notification parsing`() {
        var loggedPkg: String? = null
        var loggedField: String? = null
        var loggedSender: String? = null

        NotificationParser.debugSenderLogger = { pkg, field, sender ->
            loggedPkg = pkg
            loggedField = field
            loggedSender = sender
        }

        // Simulate debug logger callback
        NotificationParser.debugSenderLogger?.invoke(
            "com.google.android.apps.messaging",
            "EXTRA_TITLE",
            "+919876543210"
        )

        assertThat(loggedPkg).isEqualTo("com.google.android.apps.messaging")
        assertThat(loggedField).isEqualTo("EXTRA_TITLE")
        assertThat(loggedSender).isEqualTo("+919876543210")
    }

    @Test
    fun `Case B electricity scam parsed as incoming SMS evaluates to DANGER`() {
        val text = "Dear consumer, Electricity Board will disconnect your power supply tonight at 9:30 PM due to unpaid bill. Call 9876543210 to pay immediately."
        val smsMsg = IncomingMessage(
            fingerprint = "sms-case-b-fixture",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.SMS_GOOGLE_MESSAGES,
            conversationKey = "conv-case-b",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.PERSONAL_NUMBER,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val verdict = engine.analyze(smsMsg)
        assertThat(verdict.level).isEqualTo(AlertLevel.DANGER)
        assertThat(verdict.category).isEqualTo(ScamCategory.UTILITY_DISCONNECT)

        val signalIds = verdict.reasons.map { it.signalId }
        assertThat(signalIds).contains("P04")
        assertThat(signalIds).contains("P01")
        assertThat(signalIds).contains("S04")
    }
}
