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
}
