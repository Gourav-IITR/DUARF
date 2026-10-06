package com.duarf.engine.extract

import com.duarf.engine.model.TextSpan
import com.duarf.engine.normalize.NormalizedText
import java.net.URI

object UrlParser {

    private val SCHEME_REGEX = Regex("""(?i)\b(https?|hxxps?)://""")
    private val USERINFO_TRICK_REGEX = Regex("""^https?://([^@/]+)@([^/]+)""")

    // File extensions of interest in URL paths
    private val EXECUTABLE_EXTENSIONS = setOf(
        "apk", "xapk", "apks", "apkm", "exe", "scr", "bat", "cmd", "msi", "vbs", "jar"
    )

    fun extractUrls(normalized: NormalizedText, psl: PublicSuffixList): List<ExtractedUrl> {
        val text = normalized.normalizedText
        val urls = ArrayList<ExtractedUrl>()

        // 1. Identify explicit URLs with schemes (http://, https://, hxxp://, hxxps://)
        val candidateTokens = findUrlCandidates(text)

        for (candidate in candidateTokens) {
            val parsed = parseCandidate(candidate.rawCandidate, candidate.startNorm, candidate.endNorm, normalized, psl)
            if (parsed != null) {
                urls.add(parsed)
            }
        }

        return urls
    }

    private data class Candidate(val rawCandidate: String, val startNorm: Int, val endNorm: Int)

    private fun findUrlCandidates(text: String): List<Candidate> {
        val candidates = ArrayList<Candidate>()
        val words = text.split(' ')

        var currentIndex = 0
        for (word in words) {
            val start = text.indexOf(word, currentIndex)
            val end = start + word.length
            currentIndex = end

            if (word.isBlank()) continue

            // Clean common trailing punctuation from URL candidate
            var cleanWord = word.trim()
            val trailingPunct = setOf('.', ',', ';', '!', '?', ')', ']', '}', '>', '"', '\'')
            while (cleanWord.isNotEmpty() && trailingPunct.contains(cleanWord.last())) {
                cleanWord = cleanWord.dropLast(1)
            }

            if (isPotentialUrl(cleanWord)) {
                candidates.add(Candidate(cleanWord, start, start + cleanWord.length))
            }
        }

        return candidates
    }

    private fun isPotentialUrl(token: String): Boolean {
        if (token.length < 4) return false
        val lower = token.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("hxxp://") || lower.startsWith("hxxps://")
        ) {
            return true
        }
        // Bare domain: contains a dot or de-obfuscation marker, followed by a valid-looking TLD
        if (lower.contains(".") || lower.contains("[.]") || lower.contains("(dot)")) {
            val beforeSlash = lower.replace("[.]", ".").replace("(dot)", ".").substringBefore('/').substringBefore('?')
            val parts = beforeSlash.split('.')
            if (parts.size >= 2 && parts.last().length in 2..10 && parts.last().all { it in 'a'..'z' }) {
                return true
            }
        }
        return false
    }

    private fun parseCandidate(
        raw: String,
        startNorm: Int,
        endNorm: Int,
        normalized: NormalizedText,
        psl: PublicSuffixList
    ): ExtractedUrl? {
        var isDeobfuscated = false
        var working = raw

        // De-obfuscate hxxp/hxxps
        if (working.startsWith("hxxps://", ignoreCase = true)) {
            working = "https://" + working.substring(8)
            isDeobfuscated = true
        } else if (working.startsWith("hxxp://", ignoreCase = true)) {
            working = "http://" + working.substring(7)
            isDeobfuscated = true
        }

        // De-obfuscate [.] or (dot)
        if (working.contains("[.]") || working.contains("(dot)")) {
            working = working.replace("[.]", ".").replace("(dot)", ".")
            isDeobfuscated = true
        }

        // Prepend scheme if scheme-less bare domain
        val hasScheme = working.startsWith("http://", ignoreCase = true) || working.startsWith("https://", ignoreCase = true)
        val scheme = if (hasScheme) {
            working.substringBefore("://").lowercase()
        } else {
            null
        }

        val urlToParse = if (hasScheme) working else "http://$working"

        return try {
            val uri = URI(urlToParse)
            var host = uri.host?.lowercase()
            val userinfo = uri.userInfo

            // If URI couldn't extract host properly (e.g. invalid chars or tricky userinfo)
            if (host == null) {
                // Try manual host extraction
                val afterScheme = urlToParse.substringAfter("://")
                val hostPart = afterScheme.substringBefore('/').substringBefore('?')
                if (hostPart.contains('@')) {
                    val rawUser = hostPart.substringBeforeLast('@')
                    val rawHost = hostPart.substringAfterLast('@')
                    host = rawHost.lowercase()
                } else {
                    host = hostPart.lowercase()
                }
            }

            if (host.isNullOrEmpty() || !host.contains('.') && !isIp(host)) {
                return null
            }

            val registrable = psl.getRegistrableDomain(host)
            val tld = psl.getTld(host)
            val path = uri.path ?: ""
            val port = if (uri.port != -1) uri.port else null
            val isPunycode = host.contains("xn--")
            val isIp = isIp(host)

            // Extract file extension from path
            val pathSegments = path.split('/')
            val lastSegment = pathSegments.lastOrNull()?.substringBefore('?') ?: ""
            val ext = if (lastSegment.contains('.')) {
                lastSegment.substringAfterLast('.').lowercase()
            } else {
                null
            }

            // Map span back to original text
            val startOrig = if (startNorm in normalized.indexMap.indices) normalized.indexMap[startNorm] else 0
            val endOrigIdx = if (endNorm - 1 in normalized.indexMap.indices) normalized.indexMap[endNorm - 1] + 1 else startOrig + raw.length
            val span = TextSpan(startOrig, maxOf(startOrig, endOrigIdx))

            ExtractedUrl(
                span = span,
                rawText = raw,
                scheme = scheme,
                host = host,
                registrableDomain = registrable,
                tld = tld,
                path = path,
                port = port,
                userinfo = userinfo,
                isPunycode = isPunycode,
                isIpLiteral = isIp,
                isDeobfuscated = isDeobfuscated,
                fileExtension = ext
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isIp(host: String): Boolean {
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        return ipv4Regex.matches(host) || (host.startsWith("[") && host.endsWith("]"))
    }
}
