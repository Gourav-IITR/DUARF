// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.extract

class PublicSuffixList(suffixes: Set<String> = DEFAULT_SUFFIXES) {

    private val publicSuffixes: Set<String> = suffixes.map { it.trim().lowercase() }.toSet()

    fun getRegistrableDomain(host: String): String {
        val cleanHost = host.trim().lowercase().trimEnd('.')
        if (cleanHost.isEmpty()) return ""

        // Check if IP literal
        if (isIpAddress(cleanHost)) return cleanHost

        val parts = cleanHost.split('.')
        if (parts.size <= 1) return cleanHost

        // Find longest matching suffix from right to left
        var suffixLength = 0
        for (i in parts.indices) {
            val candidateSuffix = parts.subList(i, parts.size).joinToString(".")
            if (publicSuffixes.contains(candidateSuffix)) {
                suffixLength = parts.size - i
                break
            }
        }

        // If no suffix matched, assume last component is TLD
        if (suffixLength == 0) {
            suffixLength = 1
        }

        val domainIndex = parts.size - suffixLength - 1
        return if (domainIndex >= 0) {
            parts.subList(domainIndex, parts.size).joinToString(".")
        } else {
            cleanHost
        }
    }

    fun getTld(host: String): String {
        val cleanHost = host.trim().lowercase().trimEnd('.')
        if (isIpAddress(cleanHost)) return ""
        val lastDot = cleanHost.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < cleanHost.length - 1) {
            cleanHost.substring(lastDot + 1)
        } else {
            cleanHost
        }
    }

    private fun isIpAddress(host: String): Boolean {
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        return ipv4Regex.matches(host) || host.startsWith("[") && host.endsWith("]")
    }

    companion object {
        val DEFAULT_SUFFIXES: Set<String> = setOf(
            // India
            "in", "co.in", "net.in", "org.in", "gen.in", "firm.in", "ind.in",
            "gov.in", "nic.in", "ac.in", "edu.in", "res.in",
            // Common gTLDs
            "com", "org", "net", "edu", "gov", "mil", "int", "info", "biz",
            "online", "xyz", "top", "buzz", "site", "club", "vip", "icu",
            "app", "dev", "io", "me", "co", "ai", "cc", "tv", "to", "ru",
            "cn", "uk", "co.uk", "us", "ca", "de", "jp", "au", "com.au"
        )

        fun parseFromLines(lines: Sequence<String>): PublicSuffixList {
            val set = HashSet<String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue
                // Exclude wildcards or exception rules for simplicity in MVP
                val clean = trimmed.removePrefix("*.").removePrefix("!")
                if (clean.isNotEmpty()) {
                    set.add(clean.lowercase())
                }
            }
            return PublicSuffixList(set)
        }
    }
}
