package com.duarf.engine.ml

import com.duarf.engine.extract.ExtractionResult
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.TextSpan
import com.duarf.engine.normalize.NormalizedText
import java.lang.Character.UnicodeScript
import java.util.regex.Pattern
import kotlin.math.sqrt

data class FeaturizedMessage(
    val activeIndices: List<Int>, // Sorted distinct bucket indices
    val l2Value: Double,          // 1.0 / sqrt(k)
    val tokenAttributions: List<TokenFeatureMapping>
)

data class TokenFeatureMapping(
    val token: String,
    val span: TextSpan,
    val bucketIndices: List<Int>
)

class Featurizer(
    val log2Buckets: Int = 18
) {
    val bucketCount: Int = 1 shl log2Buckets
    private val bucketMask: Int = bucketCount - 1

    // Explicit Unicode whitespace [\p{Z}\t\n\u000B\f\r\u0085] and punctuation \p{P} without flags,
    // ensuring identical tokenization on both JVM and Android ICU (Invariant 8) while avoiding
    // Android's unsupported UNICODE_CHARACTER_CLASS flag.
    private val tokenPattern = Pattern.compile("(__[a-z0-9_]+__)|([^\\p{Z}\\t\\n\\u000B\\f\\r\\u0085\\p{P}]+)")

    fun featurize(
        message: IncomingMessage,
        normalized: NormalizedText,
        extracted: ExtractionResult
    ): FeaturizedMessage {
        // 1. Replace extracted entities with placeholders from right to left
        val replacements = ArrayList<Triple<Int, Int, String>>()

        for (url in extracted.urls) {
            replacements.add(Triple(url.span.start, url.span.end, "__url__"))
        }
        for (phone in extracted.phones) {
            replacements.add(Triple(phone.span.start, phone.span.end, "__phone__"))
        }
        for (upi in extracted.upiIds) {
            replacements.add(Triple(upi.span.start, upi.span.end, "__upi__"))
        }
        for (amt in extracted.amounts) {
            replacements.add(Triple(amt.span.start, amt.span.end, "__amount__"))
        }
        for (otp in extracted.otpCodes) {
            replacements.add(Triple(otp.span.start, otp.span.end, "__code__"))
        }
        for (file in extracted.fileNames) {
            if (file.isApkFamily) {
                replacements.add(Triple(file.span.start, file.span.end, "__file_apk__"))
            }
        }
        for (brand in extracted.brands) {
            val kindStr = brand.brandKind.name.lowercase()
            replacements.add(Triple(brand.span.start, brand.span.end, "__brand_${kindStr}__"))
        }

        // Sort descending by start to replace without offset invalidation
        replacements.sortByDescending { it.first }

        var textWithPlaceholders = normalized.normalizedText
        for ((start, end, placeholder) in replacements) {
            if (start in 0..textWithPlaceholders.length && end in start..textWithPlaceholders.length) {
                textWithPlaceholders = textWithPlaceholders.substring(0, start) + placeholder + textWithPlaceholders.substring(end)
            }
        }

        // 2. Tokenize
        val matcher = tokenPattern.matcher(textWithPlaceholders)
        val tokens = ArrayList<Pair<String, TextSpan>>()
        while (matcher.find()) {
            val tok = matcher.group()
            val truncated = if (tok.codePointCount(0, tok.length) > 30) {
                val endIdx = tok.offsetByCodePoints(0, 30)
                tok.substring(0, endIdx)
            } else {
                tok
            }
            tokens.add(Pair(truncated, TextSpan(matcher.start(), matcher.end())))
        }

        // 3. Generate Features
        val featureSet = HashSet<Int>()
        val tokenMappings = ArrayList<TokenFeatureMapping>()

        for (i in tokens.indices) {
            val (token, span) = tokens[i]
            val tokenBuckets = ArrayList<Int>()

            // Word unigram: w|<token>
            val unigramHash = hashBucket("w|$token")
            featureSet.add(unigramHash)
            tokenBuckets.add(unigramHash)

            // Word bigram: b|<t1> <t2>
            if (i > 0) {
                val prevToken = tokens[i - 1].first
                val bigramHash = hashBucket("b|$prevToken $token")
                featureSet.add(bigramHash)
                tokenBuckets.add(bigramHash)
            }

            // Character n-grams (3, 4, 5-grams) over ^token$ (omit for placeholders)
            val isPlaceholder = token.startsWith("__") && token.endsWith("__")
            if (!isPlaceholder && token.length >= 2) {
                val wrapped = "^$token$"
                val codePointCount = wrapped.codePointCount(0, wrapped.length)
                for (n in 3..5) {
                    if (codePointCount >= n) {
                        for (startCp in 0..(codePointCount - n)) {
                            val startIdx = wrapped.offsetByCodePoints(0, startCp)
                            val endIdx = wrapped.offsetByCodePoints(startIdx, n)
                            val gram = wrapped.substring(startIdx, endIdx)
                            val gramHash = hashBucket("c|$gram")
                            featureSet.add(gramHash)
                            tokenBuckets.add(gramHash)
                        }
                    }
                }
            }

            tokenMappings.add(TokenFeatureMapping(token, span, tokenBuckets))
        }

        // 4. Meta features: m|<name> (CRITICAL: Never uses rule signal IDs)
        val metaFeatures = ArrayList<String>()
        metaFeatures.add("m|sender_${message.senderKind.name.lowercase()}")
        metaFeatures.add("m|is_group_${message.isGroup}")

        val textLen = message.text.length
        val lenBucket = when {
            textLen <= 50 -> "0_50"
            textLen <= 150 -> "51_150"
            textLen <= 300 -> "151_300"
            else -> "301plus"
        }
        metaFeatures.add("m|len_$lenBucket")

        val urlCount = extracted.urls.size
        val urlBucket = when (urlCount) {
            0 -> "0"
            1 -> "1"
            else -> "2plus"
        }
        metaFeatures.add("m|url_count_$urlBucket")

        var hasLatin = false
        var hasDevanagari = false
        for (cp in normalized.normalizedText.codePoints()) {
            val script = UnicodeScript.of(cp)
            if (script == UnicodeScript.LATIN) hasLatin = true
            if (script == UnicodeScript.DEVANAGARI) hasDevanagari = true
        }

        if (hasLatin) metaFeatures.add("m|script_latn")
        if (hasDevanagari) metaFeatures.add("m|script_deva")

        for (url in extracted.urls) {
            val tld = url.registrableDomain.substringAfterLast('.', "")
            if (tld.isNotEmpty()) {
                metaFeatures.add("m|tld_$tld")
            }
        }

        if (extracted.fileNames.any { it.isApkFamily }) metaFeatures.add("m|has_apk")
        if (extracted.upiIds.isNotEmpty()) metaFeatures.add("m|has_upi")
        if (extracted.phones.isNotEmpty()) metaFeatures.add("m|has_phone")
        if (extracted.otpCodes.isNotEmpty()) metaFeatures.add("m|has_code")
        if (extracted.amounts.isNotEmpty()) metaFeatures.add("m|has_amount")

        for (meta in metaFeatures) {
            featureSet.add(hashBucket(meta))
        }

        val sortedIndices = featureSet.sorted()
        val l2 = if (sortedIndices.isEmpty()) 0.0 else 1.0 / sqrt(sortedIndices.size.toDouble())

        return FeaturizedMessage(
            activeIndices = sortedIndices,
            l2Value = l2,
            tokenAttributions = tokenMappings
        )
    }

    private fun hashBucket(feature: String): Int {
        return MurmurHash3.hash32(feature, 0) and bucketMask
    }
}
