package com.duarf.engine.explain

import com.duarf.engine.model.Reason
import com.duarf.engine.model.TextSpan
import com.duarf.engine.signal.FiredCombo
import com.duarf.engine.signal.FiredSignal

object ExplanationEngine {

    fun generateReasonsAndHighlights(
        signals: List<FiredSignal>,
        topCombo: FiredCombo?,
        modelHighlights: List<TextSpan> = emptyList(),
        originalText: String = ""
    ): Pair<List<Reason>, List<TextSpan>> {
        if (signals.isEmpty()) {
            if (modelHighlights.isNotEmpty()) {
                val expandedModel = if (originalText.isNotEmpty()) {
                    mergeOverlappingSpans(modelHighlights.map { expandToWholeToken(originalText, it) })
                } else {
                    modelHighlights
                }
                val modelReason = Reason(
                    signalId = "MODEL",
                    titleKey = "reason_model_title",
                    detailKey = "reason_model_detail",
                    args = emptyMap(),
                    evidence = expandedModel.firstOrNull()
                )
                return Pair(listOf(modelReason), expandedModel)
            }
            return Pair(emptyList(), emptyList())
        }

        // 1. Sort fired signals: combo member signals first, then by effective weight descending
        // 1. Sort fired signals: combo member signals first, then by effective weight descending,
        // then by specificity ranking on ties (e.g. L09 over L02 for government/police claim)
        val comboMembers = topCombo?.memberSignalIds ?: emptySet()
        val sorted = signals.sortedWith { a, b ->
            val aInCombo = if (comboMembers.contains(a.signalId)) 1 else 0
            val bInCombo = if (comboMembers.contains(b.signalId)) 1 else 0
            if (aInCombo != bInCombo) {
                bInCombo.compareTo(aInCombo)
            } else {
                val weightCmp = b.weight.compareTo(a.weight)
                if (weightCmp != 0) {
                    weightCmp
                } else {
                    val specA = getSpecificityRank(a.signalId)
                    val specB = getSpecificityRank(b.signalId)
                    if (specA != specB) {
                        specB.compareTo(specA)
                    } else {
                        a.signalId.compareTo(b.signalId)
                    }
                }
            }
        }

        // 2. Collapse signals in the same family (L, A, P, S, T)
        // Show at most 3 reasons
        val selectedReasons = ArrayList<Reason>()
        val seenFamilies = HashSet<Char>()

        for (s in sorted) {
            val family = s.signalId[0]
            // Allow combo members even if from same family, but general rule is diversify across families
            if (!seenFamilies.contains(family) || comboMembers.contains(s.signalId)) {
                seenFamilies.add(family)
                selectedReasons.add(
                    Reason(
                        signalId = s.signalId,
                        titleKey = "reason_${s.signalId.lowercase()}_title",
                        detailKey = "reason_${s.signalId.lowercase()}_detail",
                        args = s.args,
                        evidence = s.evidenceSpan
                    )
                )
                if (selectedReasons.size >= 3) break
            }
        }

        // If fewer than 3 reasons and we had more signals, fill in without family restriction
        if (selectedReasons.size < 3) {
            for (s in sorted) {
                if (selectedReasons.none { it.signalId == s.signalId }) {
                    selectedReasons.add(
                        Reason(
                            signalId = s.signalId,
                            titleKey = "reason_${s.signalId.lowercase()}_title",
                            detailKey = "reason_${s.signalId.lowercase()}_detail",
                            args = s.args,
                            evidence = s.evidenceSpan
                        )
                    )
                    if (selectedReasons.size >= 3) break
                }
            }
        }

        // 4. Highlights are the evidence spans of shown reasons + model highlights
        val rawHighlights = ArrayList<TextSpan>()
        val signalsById = signals.associateBy { it.signalId }
        for (r in selectedReasons) {
            val signal = signalsById[r.signalId]
            val spans = signal?.allEvidenceSpans?.ifEmpty { listOfNotNull(r.evidence) } ?: listOfNotNull(r.evidence)
            for (evidence in spans) {
                if (evidence.start < evidence.end) {
                    val span = if (originalText.isNotEmpty()) expandToWholeToken(originalText, evidence) else evidence
                    rawHighlights.add(span)
                }
            }
        }
        for (span in modelHighlights) {
            if (span.start < span.end) {
                val expanded = if (originalText.isNotEmpty()) expandToWholeToken(originalText, span) else span
                rawHighlights.add(expanded)
            }
        }

        val mergedHighlights = mergeOverlappingSpans(rawHighlights)
        return Pair(selectedReasons, mergedHighlights)
    }

    fun expandToWholeToken(text: String, span: TextSpan): TextSpan {
        var start = span.start.coerceIn(0, text.length)
        var end = span.end.coerceIn(0, text.length)
        if (start >= end) return TextSpan(start, end)

        // Expand left
        while (start > 0) {
            val prev = text[start - 1]
            if (prev.isWhitespace() || isHardDelimiter(prev)) break

            // Hyphen: include only if preceded by token char
            if (prev == '-' || prev == '\u2010') {
                if (start - 1 > 0 && isWordOrEmojiChar(text, start - 2)) {
                    start--
                    continue
                } else {
                    break
                }
            }

            // Comma or dot: include only if preceded by digit and followed by digit
            if (prev == ',' || prev == '.') {
                if (start - 1 > 0 && text[start - 2].isDigit() && start < text.length && text[start].isDigit()) {
                    start--
                    continue
                } else {
                    break
                }
            }

            // Low surrogate: include high surrogate before it
            if (Character.isLowSurrogate(prev)) {
                if (start - 2 >= 0 && Character.isHighSurrogate(text[start - 2])) {
                    start -= 2
                    continue
                }
            }

            // Token char
            if (isTokenChar(text, start - 1)) {
                start--
            } else {
                break
            }
        }

        // Expand right
        while (end < text.length) {
            val next = text[end]
            if (next.isWhitespace() || isHardDelimiter(next)) break

            // Hyphen: include only if followed by token char
            if (next == '-' || next == '\u2010') {
                if (end + 1 < text.length && isWordOrEmojiChar(text, end + 1)) {
                    end++
                    continue
                } else {
                    break
                }
            }

            // Comma or dot: include only if surrounded by digits
            if (next == ',' || next == '.') {
                if (end > 0 && text[end - 1].isDigit() && end + 1 < text.length && text[end + 1].isDigit()) {
                    end++
                    continue
                } else {
                    break
                }
            }

            // High surrogate: include low surrogate after it
            if (Character.isHighSurrogate(next)) {
                if (end + 1 < text.length && Character.isLowSurrogate(text[end + 1])) {
                    end += 2
                    continue
                }
            }

            // Combining mark / Matra / virama / ZWJ / variation selector
            val type = Character.getType(next)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                next == '\u200D' || next == '\uFE0F'
            ) {
                end++
                continue
            }

            // Token char
            if (isTokenChar(text, end)) {
                end++
            } else {
                break
            }
        }

        return TextSpan(start, end)
    }

    private fun isHardDelimiter(c: Char): Boolean {
        return c in "\n\r\t!?;:\"'()[]{}<>" ||
                c == '\u201C' || c == '\u201D' || c == '\u2018' || c == '\u2019'
    }

    private fun isWordOrEmojiChar(text: String, idx: Int): Boolean {
        if (idx !in text.indices) return false
        val c = text[idx]
        return Character.isLetterOrDigit(c) || isTokenChar(text, idx)
    }

    private fun isTokenChar(text: String, idx: Int): Boolean {
        val c = text[idx]
        if (Character.isLetterOrDigit(c)) return true
        if (c == '₹' || c == '$' || c == '€' || c == '£' || c == '¥' || Character.getType(c) == Character.CURRENCY_SYMBOL.toInt()) return true
        val type = Character.getType(c)
        if (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            c == '\u200D' || c == '\uFE0F'
        ) return true
        if (Character.isSurrogate(c)) return true
        return false
    }

    fun mergeOverlappingSpans(spans: List<TextSpan>): List<TextSpan> {
        if (spans.size <= 1) return spans
        val sorted = spans.sortedWith(compareBy({ it.start }, { it.end }))
        val merged = ArrayList<TextSpan>()
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end) {
                current = TextSpan(current.start, maxOf(current.end, next.end))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private fun getSpecificityRank(signalId: String): Int {
        return when (signalId) {
            "L09" -> 10 // Specific gov/police claim on non-gov domain over generic L02 brand domain mismatch
            "L02" -> 5
            "S05" -> 10 // Specific DLT header mismatch over generic S04
            "S04" -> 5
            else -> 0
        }
    }
}
