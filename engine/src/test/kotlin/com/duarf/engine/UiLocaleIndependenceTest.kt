// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The app's UI language must never change what the engine detects.
 *
 * On Android, choosing an app language also changes the process default [Locale], so any
 * locale-sensitive call inside the engine (default-locale case folding, number formatting)
 * would make detection depend on the UI language. This runs the same multilingual messages
 * under every supported UI language, plus Turkish (the classic case-folding trap), and
 * requires identical verdicts.
 */
class UiLocaleIndependenceTest {

    private lateinit var engine: DefaultScamEngine
    private lateinit var originalLocale: Locale

    private val uiLanguageTags = listOf(
        "en", "hi", "hi-Latn", "bn", "mr", "te", "ta", "or", "gu", "kn", "ml", "pa", "tr"
    )

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        engine = DefaultScamEngine.fromPackSource(packSource = FilePackSource(rootDir))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun sampleTexts(): List<String> {
        val evalDir = File("../eval").let { if (it.exists()) it else File("eval") }
        val files = listOf("corpus.jsonl") +
            listOf("bn", "mr", "te", "ta", "or", "gu", "kn", "ml", "pa").map { "dev_$it.jsonl" }
        return files.flatMap { name ->
            File(evalDir, name).readLines()
                .filter { it.isNotBlank() }
                .take(25)
                .map { Json.parseToJsonElement(it).jsonObject.getValue("text").jsonPrimitive.content }
        }
    }

    private fun message(text: String) = IncomingMessage(
        fingerprint = "locale-test",
        source = SourceKind.NOTIFICATION,
        app = SourceApp.WHATSAPP,
        conversationKey = "conv-locale",
        senderDisplay = "+919876543210",
        senderKind = SenderKind.NUMBER_ONLY,
        senderCountryCode = "+91",
        isGroup = false,
        text = text,
        attachmentHint = null,
        receivedAtMillis = 1_760_000_000_000L
    )

    @Test
    fun `verdicts are identical whatever the UI language is`() {
        val texts = sampleTexts()
        assertThat(texts.size).isAtLeast(200)

        Locale.setDefault(Locale.ENGLISH)
        val baseline = texts.map { engine.analyze(message(it), emptyList(), Sensitivity.BALANCED) }
        assertThat(baseline.count { it.level != AlertLevel.NONE }).isGreaterThan(0)

        for (tag in uiLanguageTags) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            texts.forEachIndexed { i, text ->
                val verdict = engine.analyze(message(text), emptyList(), Sensitivity.BALANCED)
                assertThat(verdict).isEqualTo(baseline[i])
            }
        }
    }
}
