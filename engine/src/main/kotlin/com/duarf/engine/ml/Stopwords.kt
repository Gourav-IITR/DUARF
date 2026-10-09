// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.ml

/**
 * Stopwords and token filtering for model attribution highlights (§11.4).
 * Drops common grammatical particles, pronouns, auxiliary verbs, and tokens under 3 characters
 * across English, Hindi (Devanagari), and Hindi-Latin (Hinglish), ensuring highlights show
 * only tokens with meaningful positive scam attribution.
 */
object Stopwords {

    val ENGLISH_STOPWORDS: Set<String> = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "has", "had",
        "are", "were", "was", "will", "would", "shall", "should", "could", "can",
        "may", "might", "must", "been", "being", "you", "your", "yours", "yourself",
        "yourselves", "they", "them", "their", "theirs", "themselves", "our", "ours",
        "ourselves", "who", "whom", "whose", "which", "what", "where", "when", "why",
        "how", "all", "any", "both", "each", "few", "more", "most", "other", "some",
        "such", "than", "too", "very", "just", "about", "above", "after", "again",
        "also", "into", "then", "there", "these", "those", "through", "under", "while",
        "out", "over", "down", "off", "his", "her", "hers", "him", "himself", "herself",
        "its", "itself", "not", "only", "same", "here", "does", "did", "doing", "done",
        "because", "between", "during", "before", "once", "until", "below", "further",
        "having", "myself", "please", "since", "unless", "although", "though"
    )

    val HINDI_STOPWORDS: Set<String> = setOf(
        "और", "तथा", "एवं", "लेकिन", "किंतु", "परंतु", "मगर", "क्योंकि", "इसलिए",
        "यानी", "अर्थात", "होता", "होती", "होते", "होगा", "होगी", "होंगे", "रहा",
        "रही", "रहे", "गया", "गई", "गए", "गयी", "दिया", "दिए", "दीं", "लिया",
        "लिए", "लीं", "वाले", "वाली", "वाला", "सकता", "सकती", "सकते", "अपना",
        "अपनी", "अपने", "इसका", "इसकी", "इसके", "उसका", "उसकी", "उसके", "उनका",
        "उनकी", "उनके", "हमारा", "हमारी", "हमारे", "तुम्हारा", "तुम्हारी", "तुम्हारे",
        "आपका", "आपकी", "आपके", "इनका", "इनकी", "इनके", "कहा", "कहे", "करना",
        "करने", "करनी", "हुआ", "हुई", "हुए", "जैसे", "वैसा", "वैसी", "वैसे",
        "वहाँ", "यहाँ", "कहाँ", "जहाँ", "किधर", "उधर", "इधर", "बहुत", "ज्यादा",
        "थोड़ा", "सभी", "कुछ", "कोई", "कौन", "क्या", "कब", "कैसे", "क्यों",
        "वही", "यही", "तरह", "बारे", "तरफ", "कारण", "समय", "बात", "साथ",
        "बिना", "अंदर", "बाहर", "ऊपर", "नीचे", "पहले", "बाद", "जब", "तब",
        "तक", "खुद", "स्वयं"
    )

    val HINDI_LATIN_STOPWORDS: Set<String> = setOf(
        "aur", "tatha", "evam", "lekin", "kintu", "parantu", "magar", "kyunki", "isliye",
        "yani", "arthat", "hota", "hoti", "hote", "hoga", "hogi", "honge", "raha",
        "rahi", "rahe", "gaya", "gayi", "gaye", "diya", "diye", "liya", "liye",
        "wale", "wali", "wala", "sakta", "sakti", "sakte", "apna", "apni", "apne",
        "iska", "iski", "iske", "uska", "uski", "uske", "unka", "unki", "unke",
        "hamara", "hamari", "hamare", "tumhara", "tumhari", "tumhare", "aapka", "aapki",
        "aapke", "inka", "inki", "inke", "kaha", "kahe", "karna", "karne", "karni",
        "hua", "hui", "hue", "tha", "thi", "the", "hain", "jaise", "waisa",
        "waisi", "waise", "wahan", "yahan", "kahan", "jahan", "kidhar", "udhar", "idhar",
        "bahut", "jyada", "thoda", "sabhi", "kuch", "koi", "kaun", "kya", "kab",
        "kaise", "kyon", "kyu", "kyun", "wahi", "yahi", "tarah", "bare", "taraf",
        "karan", "samay", "baat", "saath", "sath", "bina", "andar", "bahar", "upar",
        "neeche", "pehle", "baad", "jab", "tab", "tak", "khud", "swayam"
    )

    val ALL_STOPWORDS: Set<String> = ENGLISH_STOPWORDS + HINDI_STOPWORDS + HINDI_LATIN_STOPWORDS

    /**
     * Returns true if a token is eligible for model attribution highlight.
     * Requires:
     * 1. Not an internal featurizer placeholder (e.g. __url__, __phone__)
     * 2. Length >= 3 characters (code points)
     * 3. Not in the stopword list for English, Hindi, or Hindi-Latin
     */
    fun isMeaningfulModelToken(token: String): Boolean {
        if (token.startsWith("__") && token.endsWith("__")) {
            return false
        }
        if (token.codePointCount(0, token.length) < 3) {
            return false
        }
        val lower = token.lowercase()
        if (ALL_STOPWORDS.contains(lower)) {
            return false
        }
        return true
    }
}
