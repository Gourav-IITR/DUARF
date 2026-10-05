package com.duarf.engine.signal

object DamerauLevenshtein {

    /**
     * Computes the Damerau-Levenshtein distance between two strings,
     * which counts insertions, deletions, substitutions, and transpositions.
     */
    fun distance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val d = Array(len1 + 2) { IntArray(len2 + 2) }
        val maxDist = len1 + len2
        d[0][0] = maxDist

        for (i in 0..len1) {
            d[i + 1][0] = maxDist
            d[i + 1][1] = i
        }
        for (j in 0..len2) {
            d[0][j + 1] = maxDist
            d[1][j + 1] = j
        }

        val da = HashMap<Char, Int>()
        for (i in 1..len1) {
            var db = 0
            for (j in 1..len2) {
                val i1 = da[s2[j - 1]] ?: 0
                val j1 = db
                val cost = if (s1[i - 1] == s2[j - 1]) {
                    db = j
                    0
                } else {
                    1
                }

                d[i + 1][j + 1] = minOf(
                    d[i][j + 1] + 1,                 // deletion
                    d[i + 1][j] + 1,                 // insertion
                    d[i][j] + cost,                  // substitution
                    d[i1][j1] + (i - i1 - 1) + 1 + (j - j1 - 1) // transposition
                )
            }
            da[s1[i - 1]] = i
        }

        return d[len1 + 1][len2 + 1]
    }
}
