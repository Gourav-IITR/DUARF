// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.normalize

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NormalizerTest {

    @Test
    fun `removes invisible characters and records flag`() {
        val input = "Hello\u200BWorld\u2060Test\uFEFFDone\u00ADNow"
        val result = TextNormalizer.normalize(input)

        assertThat(result.normalizedText).isEqualTo("helloworldtestdonenow")
        assertThat(result.hadInvisibleChars).isTrue()
    }

    @Test
    fun `preserves ZWJ and ZWNJ when between Indic letters`() {
        // Hindi: क् + ZWJ + त (Devanagari \u0915 \u094D \u200D \u0924)
        val indicWithZwj = "\u0915\u094D\u200D\u0924"
        val result1 = TextNormalizer.normalize(indicWithZwj)
        assertThat(result1.normalizedText).contains("\u200D")
        assertThat(result1.hadInvisibleChars).isFalse()

        // Latin with ZWJ should strip ZWJ
        val latinWithZwj = "a\u200Db"
        val result2 = TextNormalizer.normalize(latinWithZwj)
        assertThat(result2.normalizedText).isEqualTo("ab")
        assertThat(result2.hadInvisibleChars).isTrue()
    }

    @Test
    fun `folds Cyrillic lookalikes in Latin context`() {
        // Cyrillic 'а' (U+0430) and 'о' (U+043E) mixed inside Latin "bаnk"
        val input = "b\u0430nk"
        val result = TextNormalizer.normalize(input)

        assertThat(result.normalizedText).isEqualTo("bank")
        assertThat(result.hadMixedScriptToken).isTrue()
    }

    @Test
    fun `maps Indic digits to ASCII digits`() {
        // Devanagari १२३४५ -> 12345
        val input = "पिन: १२३४५"
        val result = TextNormalizer.normalize(input)

        assertThat(result.normalizedText).contains("12345")
    }

    @Test
    fun `collapses letter spacing and digit swaps in deobfuscated text`() {
        val input = "Dear customer, update your K Y C now or 0TP will expire"
        val result = TextNormalizer.normalize(input)

        assertThat(result.deobfuscatedText).contains("kyc")
        assertThat(result.deobfuscatedText).contains("otp")
    }

    @Test
    fun `offset map maps normalized indices back to original characters`() {
        val input = "  Hello   World!  "
        val result = TextNormalizer.normalize(input)

        assertThat(result.normalizedText).isEqualTo("hello world!")
        // Verify index map points to valid original characters
        for (i in result.normalizedText.indices) {
            val origIdx = result.indexMap[i]
            val origChar = input[origIdx]
            val normChar = result.normalizedText[i]
            assertThat(origChar.lowercaseChar()).isEqualTo(normChar)
        }
    }

    @Test
    fun `handles empty and whitespace only inputs gracefully`() {
        val emptyResult = TextNormalizer.normalize("")
        assertThat(emptyResult.normalizedText).isEmpty()
        assertThat(emptyResult.indexMap).isEmpty()

        val spaceResult = TextNormalizer.normalize("   \n\t  ")
        assertThat(spaceResult.normalizedText).isEmpty()
    }

    @Test
    fun `truncates text longer than 4000 characters`() {
        val longText = "a".repeat(5000)
        val result = TextNormalizer.normalize(longText)

        assertThat(result.isTruncated).isTrue()
        assertThat(result.originalText.length).isEqualTo(4000)
        assertThat(result.normalizedText.length).isEqualTo(4000)
    }
}
