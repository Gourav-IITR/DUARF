package com.duarf.engine.extract

import com.duarf.engine.model.TextSpan
import com.duarf.engine.normalize.NormalizedText

class EntityExtractor(
    private val psl: PublicSuffixList,
    private val brands: List<BrandDefinition>,
    private val upiHandles: Set<String>
) {

    private val apkExtensions = setOf("apk", "xapk", "apks", "apkm")
    private val executableExtensions = setOf("apk", "xapk", "apks", "apkm", "exe", "scr")

    // General international phone with country code: +XX followed by 7-12 digits
    private val internationalPhoneRegex = Regex("""\+(\d{1,3})[\-\s]?(\d{7,12})\b""")

    // Indian mobile phone regex: either explicitly +91, 0, or bare 10-digit number starting with 6-9 not preceded by + or digit
    private val indianPhoneRegex = Regex("""(?<![\+\d])(?:(?:\+91[\-\s]?|0)?([6-9]\d{9}))(?![\d])""")

    // UPI ID regex: local@handle (handle must not contain dots to distinguish from email, unless handle is known)
    private val upiRegex = Regex("""\b([a-zA-Z0-9\.\-_]{2,50})@([a-zA-Z0-9]{2,30})\b""")

    // Currency/Amount regex
    private val amountRegex = Regex("""(?:₹|rs\.?|inr|rupees?)\s*([\d,]+(?:\.\d{1,2})?)(?:\s*(lakhs?|crores?|k|thousand))?""", RegexOption.IGNORE_CASE)

    // OTP / PIN code regex: 4-8 digits near keywords
    private val otpKeywordRegex = Regex("""(?i)\b(otp|pin|code|password|passcode|verification\s*code|one\s*time\s*password)\b""")
    private val digitsRegex = Regex("""\b(\d{4,8})\b""")

    fun extract(normalized: NormalizedText): ExtractionResult {
        val urls = UrlParser.extractUrls(normalized, psl)
        val fileNames = extractFileNames(normalized)
        val phones = extractPhones(normalized)
        val upiIds = extractUpiIds(normalized)
        val amounts = extractAmounts(normalized)
        val otpCodes = extractOtpCodes(normalized)
        val extractedBrands = extractBrands(normalized)

        return ExtractionResult(
            urls = urls,
            fileNames = fileNames,
            phones = phones,
            upiIds = upiIds,
            amounts = amounts,
            otpCodes = otpCodes,
            brands = extractedBrands
        )
    }

    private fun extractFileNames(normalized: NormalizedText): List<ExtractedFileName> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedFileName>()

        val tokens = text.split(' ')
        for (token in tokens) {
            val clean = token.trim(',', ';', '!', '?', '"', '\'')
            val dotIdx = clean.lastIndexOf('.')
            if (dotIdx > 0 && dotIdx < clean.length - 1) {
                val ext = clean.substring(dotIdx + 1).lowercase()
                if (executableExtensions.contains(ext)) {
                    val isApk = apkExtensions.contains(ext)
                    val beforeExt = clean.substring(0, dotIdx)
                    val secondDot = beforeExt.lastIndexOf('.')
                    val isDouble = secondDot > 0 && secondDot < beforeExt.length - 1

                    val span = findSpan(normalized, clean)
                    results.add(
                        ExtractedFileName(
                            span = span,
                            rawText = clean,
                            fileName = clean,
                            extension = ext,
                            isDoubleExtension = isDouble,
                            isApkFamily = isApk
                        )
                    )
                }
            }
        }

        return results
    }

    private fun extractPhones(normalized: NormalizedText): List<ExtractedPhone> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedPhone>()
        val coveredRanges = ArrayList<IntRange>()

        // 1. First extract international numbers with leading '+'
        for (match in internationalPhoneRegex.findAll(text)) {
            val countryCode = match.groupValues[1]
            val rest = match.groupValues[2]
            val range = match.range
            coveredRanges.add(range)
            val span = mapMatchSpan(normalized, range)

            if (countryCode == "91" && rest.length == 10 && rest[0] in '6'..'9') {
                results.add(
                    ExtractedPhone(
                        span = span,
                        rawText = match.value,
                        normalizedNumber = "+91$rest",
                        isIndianMobile = true,
                        countryCode = "+91"
                    )
                )
            } else {
                results.add(
                    ExtractedPhone(
                        span = span,
                        rawText = match.value,
                        normalizedNumber = "+$countryCode$rest",
                        isIndianMobile = false,
                        countryCode = "+$countryCode"
                    )
                )
            }
        }

        // 2. Extract Indian numbers (with 0 prefix or bare) not overlapping with already extracted international numbers
        for (match in indianPhoneRegex.findAll(text)) {
            val range = match.range
            val overlaps = coveredRanges.any { it.contains(range.first) || it.contains(range.last) || range.contains(it.first) }
            if (!overlaps) {
                val tenDigits = match.groupValues[1]
                val span = mapMatchSpan(normalized, range)
                results.add(
                    ExtractedPhone(
                        span = span,
                        rawText = match.value,
                        normalizedNumber = "+91$tenDigits",
                        isIndianMobile = true,
                        countryCode = "+91"
                    )
                )
            }
        }

        return results
    }

    private fun extractUpiIds(normalized: NormalizedText): List<ExtractedUpiId> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedUpiId>()

        for (match in upiRegex.findAll(text)) {
            val local = match.groupValues[1]
            val handle = match.groupValues[2].lowercase()

            val isValidUpi = upiHandles.contains(handle) || upiHandles.contains("@$handle") || isKnownBankUpiHandle(handle)
            if (isValidUpi) {
                val span = mapMatchSpan(normalized, match.range)
                results.add(
                    ExtractedUpiId(
                        span = span,
                        rawText = match.value,
                        localPart = local,
                        handle = handle
                    )
                )
            }
        }

        return results
    }

    private fun isKnownBankUpiHandle(handle: String): Boolean {
        val commonHandles = setOf("okaxis", "okhdfcbank", "oksbi", "okicici", "ybl", "ibl", "axl", "paytm", "apl", "upi")
        return commonHandles.contains(handle)
    }

    private fun extractAmounts(normalized: NormalizedText): List<ExtractedAmount> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedAmount>()

        for (match in amountRegex.findAll(text)) {
            val numStr = match.groupValues[1].replace(",", "")
            val multiplier = match.groupValues.getOrNull(2)?.lowercase() ?: ""
            val baseVal = numStr.toDoubleOrNull()
            val finalVal = if (baseVal != null) {
                when {
                    multiplier.startsWith("lakh") -> baseVal * 100_000
                    multiplier.startsWith("crore") -> baseVal * 10_000_000
                    multiplier == "k" || multiplier == "thousand" -> baseVal * 1000
                    else -> baseVal
                }
            } else null

            val span = mapMatchSpan(normalized, match.range)
            results.add(
                ExtractedAmount(
                    span = span,
                    rawText = match.value,
                    amount = finalVal,
                    currency = "INR"
                )
            )
        }

        return results
    }

    private fun extractOtpCodes(normalized: NormalizedText): List<ExtractedOtpCode> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedOtpCode>()

        val keywordMatches = otpKeywordRegex.findAll(text).toList()
        if (keywordMatches.isEmpty()) return results

        for (match in digitsRegex.findAll(text)) {
            val code = match.groupValues[1]
            val codeStart = match.range.first

            val isNearKeyword = keywordMatches.any { kw ->
                val dist = minOf(
                    Math.abs(codeStart - kw.range.first),
                    Math.abs(codeStart - kw.range.last)
                )
                dist <= 60
            }

            if (isNearKeyword) {
                val span = mapMatchSpan(normalized, match.range)
                results.add(
                    ExtractedOtpCode(
                        span = span,
                        rawText = code,
                        code = code
                    )
                )
            }
        }

        return results
    }

    private fun extractBrands(normalized: NormalizedText): List<ExtractedBrand> {
        val text = normalized.normalizedText
        val results = ArrayList<ExtractedBrand>()

        for (brand in brands) {
            for (name in brand.names) {
                val lowerName = name.lowercase().trim()
                if (lowerName.isEmpty()) continue

                var startIndex = 0
                while (startIndex < text.length) {
                    val found = text.indexOf(lowerName, startIndex)
                    if (found == -1) break

                    val end = found + lowerName.length
                    val isStartBoundary = found == 0 || !text[found - 1].isLetterOrDigit()
                    val isEndBoundary = end == text.length || !text[end].isLetterOrDigit()

                    if (isStartBoundary && isEndBoundary) {
                        val span = mapRangeToOriginal(normalized, found, end)
                        results.add(
                            ExtractedBrand(
                                span = span,
                                rawText = text.substring(found, end),
                                brandId = brand.id,
                                brandName = brand.names.first(),
                                brandKind = brand.kind,
                                officialDomains = brand.officialDomains,
                                isVerified = brand.isVerified
                            )
                        )
                    }

                    startIndex = found + 1
                }
            }
        }

        return results
    }

    private fun findSpan(normalized: NormalizedText, token: String): TextSpan {
        val idx = normalized.normalizedText.indexOf(token)
        return if (idx >= 0) {
            mapRangeToOriginal(normalized, idx, idx + token.length)
        } else {
            TextSpan(0, 0)
        }
    }

    private fun mapMatchSpan(normalized: NormalizedText, range: IntRange): TextSpan =
        mapRangeToOriginal(normalized, range.first, range.last + 1)

    private fun mapRangeToOriginal(normalized: NormalizedText, startNorm: Int, endNorm: Int): TextSpan {
        val map = normalized.indexMap
        if (map.isEmpty()) return TextSpan(0, 0)

        val s = if (startNorm in map.indices) map[startNorm] else 0
        val e = if (endNorm - 1 in map.indices) map[endNorm - 1] + 1 else s + (endNorm - startNorm)
        return TextSpan(s, maxOf(s, e))
    }
}
