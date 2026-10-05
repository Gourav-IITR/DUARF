package com.duarf.engine

import com.duarf.engine.explain.ExplanationEngine
import com.duarf.engine.extract.*
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.LoadedPacks
import com.duarf.engine.pack.PackLoader
import com.duarf.engine.pack.PackSource
import com.duarf.engine.signal.*

class DefaultScamEngine(
    private val packs: LoadedPacks,
    private val engineVersion: String = "1.0.0-rules"
) : ScamEngine {

    private val psl: PublicSuffixList = if (packs.pslLines.isNotEmpty()) {
        PublicSuffixList.parseFromLines(packs.pslLines.asSequence())
    } else {
        PublicSuffixList()
    }

    private val entityExtractor: EntityExtractor = EntityExtractor(
        psl = psl,
        brands = packs.brands,
        upiHandles = packs.upiHandles
    )

    private val ahoCorasick: AhoCorasick
    private val signalEngine: SignalEngine

    init {
        // Compile all phrase lexicons from all language packs into a single Aho-Corasick automaton (§7.1)
        val patterns = ArrayList<LexiconPattern>()
        for (lang in packs.languages) {
            for ((intent, phrases) in lang.lexicons) {
                for (phrase in phrases) {
                    patterns.add(LexiconPattern(intent, phrase))
                }
            }
        }
        ahoCorasick = AhoCorasick(patterns)

        signalEngine = SignalEngine(
            ahoCorasick = ahoCorasick,
            shorteners = packs.shorteners,
            riskyTlds = packs.riskyTlds,
            remoteApps = packs.remoteApps,
            blocklist = packs.blocklist
        )
    }

    override fun analyze(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        sensitivity: Sensitivity
    ): Verdict {
        val startTime = System.currentTimeMillis()

        return try {
            // 1. Normalize current message (§6.1)
            val normalized = TextNormalizer.normalize(message.text)

            // 2. Extract entities from current message (§6.2)
            val extracted = entityExtractor.extract(normalized)

            // 3. Evaluate signals for current message (§6.3, §7)
            val (mainSignals, dampeners) = signalEngine.evaluate(
                message = message,
                normalized = normalized,
                extracted = extracted,
                context = context,
                isTrustedSender = false,
                messageCountForSender = context.size + 1
            )

            // 4. Evaluate context messages (§5.4): signals found only in context count at half weight
            val allSignals = ArrayList<FiredSignal>(mainSignals)
            val currentSignalIds = mainSignals.map { it.signalId }.toSet()

            for (ctxMsg in context) {
                // Check 250ms timeout guard (§6)
                if (System.currentTimeMillis() - startTime > 240) break

                val ctxNorm = TextNormalizer.normalize(ctxMsg.text)
                val ctxExtracted = entityExtractor.extract(ctxNorm)
                val (ctxSignals, _) = signalEngine.evaluate(
                    message = ctxMsg,
                    normalized = ctxNorm,
                    extracted = ctxExtracted,
                    context = emptyList()
                )

                for (s in ctxSignals) {
                    if (!currentSignalIds.contains(s.signalId) && allSignals.none { it.signalId == s.signalId }) {
                        allSignals.add(s.copy(isFromContext = true))
                    }
                }
            }

            // 5. Evaluate combos (§7.3)
            val combos = ComboEngine.evaluateCombos(allSignals)

            // 6. Score fusion (§10) - model probability is null in M1 (rules only)
            val fusion = ScoreFusion.fuse(
                signals = allSignals,
                dampeners = dampeners,
                combos = combos,
                modelProbability = null,
                sensitivity = sensitivity
            )

            // 7. Explanations and highlights (§11)
            val (reasons, highlights) = ExplanationEngine.generateReasonsAndHighlights(
                signals = allSignals,
                topCombo = fusion.topCombo
            )

            Verdict(
                level = fusion.level,
                score = fusion.score,
                ruleScore = fusion.ruleScore,
                modelProbability = null,
                category = fusion.category,
                reasons = reasons,
                highlights = highlights,
                engineVersion = engineVersion
            )
        } catch (_: Exception) {
            // Degrades gracefully to NONE verdict without throwing (§6)
            Verdict(
                level = AlertLevel.NONE,
                score = 0.0,
                ruleScore = 0.0,
                modelProbability = null,
                category = ScamCategory.OTHER_SUSPICIOUS,
                reasons = emptyList(),
                highlights = emptyList(),
                engineVersion = engineVersion
            )
        }
    }

    companion object {
        fun fromPackSource(packSource: PackSource, engineVersion: String = "1.0.0-rules"): DefaultScamEngine {
            val loadedPacks = PackLoader.load(packSource)
            return DefaultScamEngine(loadedPacks, engineVersion)
        }
    }
}
