// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class ScriptGatingTest {

    private lateinit var engine: DefaultScamEngine
    private val loggedEvents = ArrayList<Int>()

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        loggedEvents.clear()
        engine = DefaultScamEngine.fromPackSource(
            packSource = FilePackSource(rootDir),
            eventSink = { loggedEvents.add(it) }
        )
    }

    private fun createMessage(text: String): IncomingMessage {
        return IncomingMessage(
            fingerprint = "test-fp",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-1",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    @Test
    fun `Bengali text gets m' = 0 and emits event 304`() {
        // Bengali script
        val msg = createMessage("আপনার বিদ্যুৎ বিল বকেয়া রয়েছে। আজ রাতে বিদ্যুৎ সংযোগ বিচ্ছিন্ন করা হবে।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.modelProbability).isNull()
        assertThat(loggedEvents).contains(304)
    }

    @Test
    fun `Telugu text gets m' = 0 and emits event 304`() {
        // Telugu script
        val msg = createMessage("మీ విద్యుత్ బిల్లు బకాయి ఉంది. ఈ రాత్రి విద్యుత్ కనెక్షన్ నిలిపివేయబడుతుంది.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.modelProbability).isNull()
        assertThat(loggedEvents).contains(304)
    }

    @Test
    fun `Tamil text gets m' = 0 and emits event 304`() {
        // Tamil script
        val msg = createMessage("உங்கள் மின் கட்டணம் நிலுவையில் உள்ளது. இன்று இரவு மின் இணைப்பு துண்டிக்கப்படும்.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.modelProbability).isNull()
        assertThat(loggedEvents).contains(304)
    }

    @Test
    fun `Odia text gets m' = 0 and emits event 304`() {
        // Odia script
        val msg = createMessage("ଆପଣଙ୍କ ବିଦ୍ୟୁତ୍ ବିଲ୍ ବାକି ଅଛି। ଆଜି ରାତିରେ ବିଦ୍ୟୁତ୍ ସଂଯୋଗ ବିଚ୍ଛିନ୍ନ ହେବ।")
        val verdict = engine.analyze(msg)

        assertThat(verdict.modelProbability).isNull()
        assertThat(loggedEvents).contains(304)
    }

    @Test
    fun `English text does not emit event 304 and runs model`() {
        // English (Latin script)
        val msg = createMessage("Your electricity bill is overdue. Power supply will be disconnected tonight.")
        val verdict = engine.analyze(msg)

        assertThat(loggedEvents).doesNotContain(304)
        if (engine.isModelLoaded) {
            assertThat(verdict.modelProbability).isNotNull()
        }
    }

    @Test
    fun `Hindi text does not emit event 304 and runs model`() {
        // Hindi (Devanagari script)
        val msg = createMessage("आपका बिजली बिल बकाया है। आज रात बिजली काट दी जाएगी।")
        val verdict = engine.analyze(msg)

        assertThat(loggedEvents).doesNotContain(304)
        if (engine.isModelLoaded) {
            assertThat(verdict.modelProbability).isNotNull()
        }
    }
}
