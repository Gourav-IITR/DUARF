package com.duarf.engine.explain

import com.duarf.engine.model.Reason
import com.duarf.engine.model.TextSpan
import com.duarf.engine.signal.FiredCombo
import com.duarf.engine.signal.FiredSignal

object ExplanationEngine {

    fun generateReasonsAndHighlights(
        signals: List<FiredSignal>,
        topCombo: FiredCombo?,
        modelHighlights: List<TextSpan> = emptyList()
    ): Pair<List<Reason>, List<TextSpan>> {
        if (signals.isEmpty()) {
            if (modelHighlights.isNotEmpty()) {
                val modelReason = Reason(
                    signalId = "MODEL",
                    titleKey = "reason_model_title",
                    detailKey = "reason_model_detail",
                    args = emptyMap(),
                    evidence = modelHighlights.firstOrNull()
                )
                return Pair(listOf(modelReason), modelHighlights)
            }
            return Pair(emptyList(), emptyList())
        }

        // 1. Sort fired signals: combo member signals first, then by effective weight descending
        val comboMembers = topCombo?.memberSignalIds ?: emptySet()
        val sorted = signals.sortedWith { a, b ->
            val aInCombo = if (comboMembers.contains(a.signalId)) 1 else 0
            val bInCombo = if (comboMembers.contains(b.signalId)) 1 else 0
            if (aInCombo != bInCombo) {
                bInCombo.compareTo(aInCombo)
            } else {
                b.weight.compareTo(a.weight)
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
        val highlights = ArrayList<TextSpan>()
        for (r in selectedReasons) {
            if (r.evidence != null && r.evidence.start < r.evidence.end) {
                highlights.add(r.evidence)
            }
        }
        for (span in modelHighlights) {
            if (span.start < span.end && highlights.none { it.start == span.start && it.end == span.end }) {
                highlights.add(span)
            }
        }

        return Pair(selectedReasons, highlights)
    }
}
