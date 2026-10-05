package com.duarf.engine.normalize

import java.text.Normalizer as JNormalizer

data class NormalizedText(
    val originalText: String,
    val normalizedText: String,
    val deobfuscatedText: String,
    val indexMap: IntArray, // maps index in normalizedText -> index in originalText
    val deobfuscatedIndexMap: IntArray, // maps index in deobfuscatedText -> index in originalText
    val hadInvisibleChars: Boolean,
    val hadMixedScriptToken: Boolean,
    val isTruncated: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NormalizedText
        return originalText == other.originalText &&
                normalizedText == other.normalizedText &&
                deobfuscatedText == other.deobfuscatedText &&
                hadInvisibleChars == other.hadInvisibleChars &&
                hadMixedScriptToken == other.hadMixedScriptToken &&
                isTruncated == other.isTruncated
    }

    override fun hashCode(): Int {
        var result = originalText.hashCode()
        result = 31 * result + normalizedText.hashCode()
        result = 31 * result + deobfuscatedText.hashCode()
        result = 31 * result + hadInvisibleChars.hashCode()
        result = 31 * result + hadMixedScriptToken.hashCode()
        result = 31 * result + isTruncated.hashCode()
        return result
    }
}

object TextNormalizer {
    private const val MAX_INPUT_LENGTH = 4000

    // Invisible characters (§6.1 step 2)
    private val INVISIBLE_CHARS = setOf(
        '\u200B', // zero-width space
        '\u2060', // word joiner
        '\uFEFF', // zero-width no-break space / BOM
        '\u00AD', // soft hyphen
        '\u200E', // LTR mark
        '\u200F', // RTL mark
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', // LTR/RTL embeddings/overrides
        '\u2066', '\u2067', '\u2068', '\u2069'             // directional isolates
    )

    private const val ZWJ = '\u200D'
    private const val ZWNJ = '\u200C'

    private val CYRILLIC_LOOKALIKES = mapOf(
        'а' to 'a', 'А' to 'A',
        'с' to 'c', 'С' to 'C',
        'е' to 'e', 'Е' to 'E',
        'о' to 'o', 'О' to 'O',
        'р' to 'p', 'Р' to 'P',
        'х' to 'x', 'Х' to 'X',
        'у' to 'y', 'У' to 'Y',
        'і' to 'i', 'І' to 'I',
        'ѕ' to 's', 'Ѕ' to 'S',
        'ј' to 'j', 'Ј' to 'J',
        'ԁ' to 'd', 'Ԃ' to 'D',
        'ԛ' to 'q',
        'ԝ' to 'w',
        'Ь' to 'B', 'ь' to 'b',
        'М' to 'M', 'м' to 'm',
        'Н' to 'H',
        'Т' to 'T', 'т' to 't',
        'В' to 'B'
    )

    private val GREEK_LOOKALIKES = mapOf(
        'α' to 'a', 'Α' to 'A',
        'β' to 'b', 'Β' to 'B',
        'γ' to 'y',
        'δ' to 'd',
        'ε' to 'e', 'Ε' to 'E',
        'ζ' to 'z', 'Ζ' to 'Z',
        'η' to 'n', 'Η' to 'H',
        'θ' to 'o', 'Θ' to 'O',
        'ι' to 'i', 'Ι' to 'I',
        'κ' to 'k', 'Κ' to 'K',
        'λ' to 'l',
        'μ' to 'u', 'Μ' to 'M',
        'ν' to 'v', 'Ν' to 'N',
        'ο' to 'o', 'Ο' to 'O',
        'ρ' to 'p', 'Ρ' to 'P',
        'τ' to 't', 'Τ' to 'T',
        'υ' to 'v', 'Υ' to 'Y',
        'χ' to 'x', 'Χ' to 'X'
    )

    fun normalize(input: String): NormalizedText {
        val isTruncated = input.length > MAX_INPUT_LENGTH
        val raw = if (isTruncated) input.substring(0, MAX_INPUT_LENGTH) else input

        var hadInvisible = false
        var hadMixedScript = false

        // Step 1: Unicode NFKC with index tracking
        // To accurately track offsets, we track each character through transformations.
        // We'll build parallel lists of Char and original index.
        val nfkcChars = ArrayList<Char>(raw.length * 2)
        val nfkcIndices = ArrayList<Int>(raw.length * 2)

        for (i in raw.indices) {
            val originalChar = raw[i]
            val normalizedCharStr = JNormalizer.normalize(originalChar.toString(), JNormalizer.Form.NFKC)
            for (c in normalizedCharStr) {
                nfkcChars.add(c)
                nfkcIndices.add(i)
            }
        }

        // Step 2: Remove invisible characters, preserving ZWJ/ZWNJ between Indic letters
        val step2Chars = ArrayList<Char>(nfkcChars.size)
        val step2Indices = ArrayList<Int>(nfkcIndices.size)

        for (i in nfkcChars.indices) {
            val c = nfkcChars[i]
            val origIdx = nfkcIndices[i]

            if (c == ZWJ || c == ZWNJ) {
                val prev = if (i > 0) nfkcChars[i - 1] else null
                val next = if (i + 1 < nfkcChars.size) nfkcChars[i + 1] else null
                if (prev != null && next != null && isIndicLetter(prev) && isIndicLetter(next)) {
                    // Keep ZWJ/ZWNJ in Indic context
                    step2Chars.add(c)
                    step2Indices.add(origIdx)
                } else {
                    hadInvisible = true
                }
            } else if (INVISIBLE_CHARS.contains(c)) {
                hadInvisible = true
            } else {
                step2Chars.add(c)
                step2Indices.add(origIdx)
            }
        }

        // Step 3: Fold Cyrillic and Greek look-alike letters inside tokens that are otherwise Latin
        // Tokenize by non-alphanumeric boundaries
        val step3Chars = ArrayList<Char>(step2Chars.size)
        val step3Indices = ArrayList<Int>(step2Indices.size)

        var tokenStart = 0
        while (tokenStart < step2Chars.size) {
            if (!step2Chars[tokenStart].isLetterOrDigit()) {
                step3Chars.add(step2Chars[tokenStart])
                step3Indices.add(step2Indices[tokenStart])
                tokenStart++
                continue
            }

            var tokenEnd = tokenStart
            while (tokenEnd < step2Chars.size && step2Chars[tokenEnd].isLetterOrDigit()) {
                tokenEnd++
            }

            // Inspect token
            var latinCount = 0
            var lookalikeCount = 0
            for (i in tokenStart until tokenEnd) {
                val c = step2Chars[i]
                if (isBasicLatinLetter(c)) {
                    latinCount++
                } else if (CYRILLIC_LOOKALIKES.containsKey(c) || GREEK_LOOKALIKES.containsKey(c)) {
                    lookalikeCount++
                }
            }

            val shouldFold = latinCount > 0 && lookalikeCount > 0
            if (shouldFold) {
                hadMixedScript = true
            }

            for (i in tokenStart until tokenEnd) {
                val c = step2Chars[i]
                val origIdx = step2Indices[i]
                if (shouldFold && (CYRILLIC_LOOKALIKES.containsKey(c) || GREEK_LOOKALIKES.containsKey(c))) {
                    val folded = CYRILLIC_LOOKALIKES[c] ?: GREEK_LOOKALIKES[c] ?: c
                    step3Chars.add(folded)
                } else {
                    step3Chars.add(c)
                }
                step3Indices.add(origIdx)
            }

            tokenStart = tokenEnd
        }

        // Step 5: Map Indic digits to ASCII digits & Lowercase Latin text
        val step5Chars = ArrayList<Char>(step3Chars.size)
        val step5Indices = ArrayList<Int>(step3Indices.size)

        for (i in step3Chars.indices) {
            val c = step3Chars[i]
            val origIdx = step3Indices[i]

            val mappedDigit = mapIndicDigitToAscii(c)
            val finalChar = if (mappedDigit != null) {
                mappedDigit
            } else if (isBasicLatinLetter(c)) {
                c.lowercaseChar()
            } else {
                c
            }

            step5Chars.add(finalChar)
            step5Indices.add(origIdx)
        }

        // Step 6: Collapse whitespace
        val normalizedChars = ArrayList<Char>(step5Chars.size)
        val normalizedIndices = ArrayList<Int>(step5Indices.size)

        var inWhitespace = false
        for (i in step5Chars.indices) {
            val c = step5Chars[i]
            val origIdx = step5Indices[i]

            if (c.isWhitespace()) {
                if (!inWhitespace && normalizedChars.isNotEmpty()) {
                    normalizedChars.add(' ')
                    normalizedIndices.add(origIdx)
                    inWhitespace = true
                }
            } else {
                normalizedChars.add(c)
                normalizedIndices.add(origIdx)
                inWhitespace = false
            }
        }

        // Trim trailing space if any
        if (normalizedChars.isNotEmpty() && normalizedChars.last() == ' ') {
            normalizedChars.removeAt(normalizedChars.size - 1)
            normalizedIndices.removeAt(normalizedIndices.size - 1)
        }

        val normText = normalizedChars.joinToString("")
        val normIndexMap = normalizedIndices.toIntArray()

        // Step 4: Deobfuscated view for lexicon matching:
        // - Collapse letter-spaced words ("k y c" -> "kyc", "o . t . p" -> "otp")
        // - Replace digit-for-letter swaps ("0tp" -> "otp", "k¥c" -> "kyc")
        val (deobText, deobIndices) = buildDeobfuscatedView(normText, normIndexMap)

        return NormalizedText(
            originalText = raw,
            normalizedText = normText,
            deobfuscatedText = deobText,
            indexMap = normIndexMap,
            deobfuscatedIndexMap = deobIndices,
            hadInvisibleChars = hadInvisible,
            hadMixedScriptToken = hadMixedScript,
            isTruncated = isTruncated
        )
    }

    private fun isIndicLetter(c: Char): Boolean {
        val code = c.code
        return (code in 0x0900..0x097F && code !in 0x0966..0x096F) || // Devanagari
                (code in 0x0980..0x09FF && code !in 0x09E6..0x09EF) || // Bengali
                (code in 0x0A00..0x0A7F && code !in 0x0A66..0x0A6F) || // Gurmukhi
                (code in 0x0A80..0x0AFF && code !in 0x0AE6..0x0AEF) || // Gujarati
                (code in 0x0B00..0x0B7F && code !in 0x0B66..0x0B6F) || // Odia
                (code in 0x0B80..0x0BFF && code !in 0x0BE6..0x0BEF) || // Tamil
                (code in 0x0C00..0x0C7F && code !in 0x0C66..0x0C6F) || // Telugu
                (code in 0x0C80..0x0CFF && code !in 0x0CE6..0x0CEF) || // Kannada
                (code in 0x0D00..0x0D7F && code !in 0x0D66..0x0D6F)    // Malayalam
    }

    private fun isBasicLatinLetter(c: Char): Boolean =
        (c in 'a'..'z') || (c in 'A'..'Z')

    private fun mapIndicDigitToAscii(c: Char): Char? {
        val code = c.code
        return when {
            code in 0x0966..0x096F -> ('0'.code + (code - 0x0966)).toChar() // Devanagari
            code in 0x09E6..0x09EF -> ('0'.code + (code - 0x09E6)).toChar() // Bengali
            code in 0x0A66..0x0A6F -> ('0'.code + (code - 0x0A66)).toChar() // Gurmukhi
            code in 0x0AE6..0x0AEF -> ('0'.code + (code - 0x0AE6)).toChar() // Gujarati
            code in 0x0B66..0x0B6F -> ('0'.code + (code - 0x0B66)).toChar() // Odia
            code in 0x0BE6..0x0BEF -> ('0'.code + (code - 0x0BE6)).toChar() // Tamil
            code in 0x0C66..0x0C6F -> ('0'.code + (code - 0x0C66)).toChar() // Telugu
            code in 0x0CE6..0x0CEF -> ('0'.code + (code - 0x0CE6)).toChar() // Kannada
            code in 0x0D66..0x0D6F -> ('0'.code + (code - 0x0D66)).toChar() // Malayalam
            else -> null
        }
    }

    private fun buildDeobfuscatedView(text: String, indexMap: IntArray): Pair<String, IntArray> {
        if (text.isEmpty()) return Pair("", IntArray(0))

        val resultChars = ArrayList<Char>(text.length)
        val resultIndices = ArrayList<Int>(text.length)

        var i = 0
        while (i < text.length) {
            val c = text[i]

            // Check for letter-spacing: single letters separated by space or dot: "k y c" or "k.y.c"
            if (isBasicLatinLetter(c) || c.isDigit()) {
                // Lookahead to see if there is a sequence of (char + space/dot + char)
                var lookAhead = i
                var letterSequence = StringBuilder()
                var seqIndices = ArrayList<Int>()

                while (lookAhead < text.length) {
                    val curr = text[lookAhead]
                    if (isBasicLatinLetter(curr) || curr.isDigit()) {
                        letterSequence.append(deobfuscateChar(curr))
                        seqIndices.add(indexMap[lookAhead])
                        lookAhead++

                        // Check if followed by single space, dot, or dash then another letter
                        if (lookAhead < text.length && (text[lookAhead] == ' ' || text[lookAhead] == '.' || text[lookAhead] == '-')) {
                            val separator = text[lookAhead]
                            if (lookAhead + 1 < text.length && (isBasicLatinLetter(text[lookAhead + 1]) || text[lookAhead + 1].isDigit())) {
                                // Only consume separator if next is another single letter/token
                                // Don't collapse normal sentences: check if next-next is also a separator or boundary
                                val afterNext = lookAhead + 2
                                val isSingleLetterFollowed = afterNext >= text.length ||
                                        text[afterNext] == ' ' || text[afterNext] == '.' || text[afterNext] == '-' ||
                                        !text[afterNext].isLetterOrDigit()

                                if (isSingleLetterFollowed || letterSequence.length >= 2) {
                                    lookAhead++ // skip separator
                                    continue
                                }
                            }
                        }
                        break
                    } else {
                        break
                    }
                }

                if (letterSequence.length >= 2) {
                    for (k in letterSequence.indices) {
                        resultChars.add(letterSequence[k])
                        resultIndices.add(seqIndices[k])
                    }
                    i = lookAhead
                    continue
                }
            }

            // Normal deobfuscate substitution
            resultChars.add(deobfuscateChar(c))
            resultIndices.add(indexMap[i])
            i++
        }

        return Pair(resultChars.joinToString(""), resultIndices.toIntArray())
    }

    private fun deobfuscateChar(c: Char): Char = when (c) {
        '0' -> 'o'
        '@' -> 'a'
        '$' -> 's'
        '!' -> 'i'
        '1' -> 'l'
        '3' -> 'e'
        '5' -> 's'
        '7' -> 't'
        else -> c
    }
}
