// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.normalize

import java.lang.Character.UnicodeScript

/**
 * Detects predominant script and language discrimination (e.g. Marathi vs Hindi in Devanagari)
 * for gating the ML model off on untrained languages / scripts (Invariant 6 & spec §8).
 */
object LanguageScriptDetector {

    private val MARATHI_FUNCTION_WORDS = setOf(
        "आहे", "आणि", "नाही", "आहेत", "करा", "केले", "होते", "येईल", "त्यांचा", "त्यांची",
        "त्यांचे", "आपला", "आपली", "आपले", "असेल", "पाहिजे", "द्या", "झाले", "वरून", "साठी"
    )

    private val HINDI_FUNCTION_WORDS = setOf(
        "है", "और", "नहीं", "हैं", "करें", "करो", "किया", "था", "थी", "थे", "होगा", "होगी",
        "होंगे", "उनका", "उनकी", "उनके", "आपका", "आपकी", "आपके", "चाहिए", "दीजिए", "दो",
        "हुआ", "हुई", "हुए", "से"
    )

    /**
     * Determines whether the predominant script of the text is untrained.
     * The model was trained only on Latin and Devanagari (en, hi, hi-Latn).
     * Returns true if the predominant script is outside {LATIN, DEVANAGARI}.
     */
    fun isUntrainedScript(text: String): Boolean {
        val scriptCounts = HashMap<UnicodeScript, Int>()
        for (cp in text.codePoints()) {
            if (Character.isLetter(cp)) {
                val script = UnicodeScript.of(cp)
                if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED && script != UnicodeScript.UNKNOWN) {
                    scriptCounts[script] = (scriptCounts[script] ?: 0) + 1
                }
            }
        }
        if (scriptCounts.isEmpty()) return false
        val predominant = scriptCounts.maxByOrNull { it.value }?.key ?: return false
        return predominant != UnicodeScript.LATIN && predominant != UnicodeScript.DEVANAGARI
    }

    /**
     * Discriminator for Marathi vs Hindi in Devanagari text using function-word ratio.
     * Returns true if the text is identified as Marathi.
     */
    fun isMarathi(text: String): Boolean {
        var hasDevanagari = false
        for (cp in text.codePoints()) {
            if (UnicodeScript.of(cp) == UnicodeScript.DEVANAGARI) {
                hasDevanagari = true
                break
            }
        }
        if (!hasDevanagari) return false

        val words = text.lowercase().split(Regex("[\\s\\p{P}]+")).filter { it.isNotEmpty() }
        var mrCount = 0
        var hiCount = 0
        for (w in words) {
            if (MARATHI_FUNCTION_WORDS.contains(w)) mrCount++
            if (HINDI_FUNCTION_WORDS.contains(w)) hiCount++
        }

        return mrCount > hiCount && mrCount >= 1
    }

    data class DetectedLanguageInfo(
        val code: String,
        val displayName: String,
        val isBeta: Boolean
    )

    private val BETA_LANGUAGE_CODES = setOf("bn", "mr", "te", "ta", "or", "gu", "kn", "ml", "pa")

    fun isBetaLanguage(code: String?): Boolean = code != null && code in BETA_LANGUAGE_CODES

    fun getLanguageInfo(code: String): DetectedLanguageInfo? = when (code) {
        "en" -> DetectedLanguageInfo("en", "English", isBeta = false)
        "hi" -> DetectedLanguageInfo("hi", "हिंदी", isBeta = false)
        "bn" -> DetectedLanguageInfo("bn", "বাংলা", isBeta = true)
        "mr" -> DetectedLanguageInfo("mr", "मराठी", isBeta = true)
        "te" -> DetectedLanguageInfo("te", "తెలుగు", isBeta = true)
        "ta" -> DetectedLanguageInfo("ta", "தமிழ்", isBeta = true)
        "or" -> DetectedLanguageInfo("or", "ଓଡ଼ିଆ", isBeta = true)
        "gu" -> DetectedLanguageInfo("gu", "ગુજરાતી", isBeta = true)
        "kn" -> DetectedLanguageInfo("kn", "ಕನ್ನಡ", isBeta = true)
        "ml" -> DetectedLanguageInfo("ml", "മലയാളം", isBeta = true)
        "pa" -> DetectedLanguageInfo("pa", "ਪੰਜਾਬੀ", isBeta = true)
        else -> null
    }

    /**
     * Detects language and beta status based on predominant script and language discrimination.
     */
    fun detectLanguageInfo(text: String): DetectedLanguageInfo? {
        val scriptCounts = HashMap<UnicodeScript, Int>()
        for (cp in text.codePoints()) {
            if (Character.isLetter(cp)) {
                val script = UnicodeScript.of(cp)
                if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED && script != UnicodeScript.UNKNOWN) {
                    scriptCounts[script] = (scriptCounts[script] ?: 0) + 1
                }
            }
        }
        if (scriptCounts.isEmpty()) return null
        val predominant = scriptCounts.maxByOrNull { it.value }?.key ?: return null

        return when (predominant) {
            UnicodeScript.BENGALI -> DetectedLanguageInfo("bn", "বাংলা", isBeta = true)
            UnicodeScript.TELUGU -> DetectedLanguageInfo("te", "తెలుగు", isBeta = true)
            UnicodeScript.TAMIL -> DetectedLanguageInfo("ta", "தமிழ்", isBeta = true)
            UnicodeScript.ORIYA -> DetectedLanguageInfo("or", "ଓଡ଼ିଆ", isBeta = true)
            UnicodeScript.GUJARATI -> DetectedLanguageInfo("gu", "ગુજરાતી", isBeta = true)
            UnicodeScript.KANNADA -> DetectedLanguageInfo("kn", "ಕನ್ನಡ", isBeta = true)
            UnicodeScript.MALAYALAM -> DetectedLanguageInfo("ml", "മലയാളം", isBeta = true)
            UnicodeScript.GURMUKHI -> DetectedLanguageInfo("pa", "ਪੰਜਾਬੀ", isBeta = true)
            UnicodeScript.DEVANAGARI -> {
                if (isMarathi(text)) {
                    DetectedLanguageInfo("mr", "मराठी", isBeta = true)
                } else {
                    DetectedLanguageInfo("hi", "हिंदी", isBeta = false)
                }
            }
            UnicodeScript.LATIN -> DetectedLanguageInfo("en", "English", isBeta = false)
            else -> null
        }
    }
}
