// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

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
    private val engineVersion: String = "1.0.0-rules",
    val gateUntrainedScripts: Boolean = true,
    private val eventSink: ((Int) -> Unit)? = null
) : ScamEngine {

    private val psl: PublicSuffixList = if (packs.pslLines.isNotEmpty()) {
        PublicSuffixList.parseFromLines(packs.pslLines.asSequence())
    } else {
        PublicSuffixList()
    }

    internal val entityExtractor: EntityExtractor = EntityExtractor(
        psl = psl,
        brands = packs.brands,
        upiHandles = packs.upiHandles
    )

    private val ahoCorasick: AhoCorasick
    internal val signalEngine: SignalEngine

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
            blocklist = packs.blocklist,
            policeDltHeaders = packs.policeDltHeaders
        )
    }

    private val featurizer: com.duarf.engine.ml.Featurizer = com.duarf.engine.ml.Featurizer()
    private val classifier: com.duarf.engine.ml.LinearClassifier? = try {
        packs.modelBytes?.let { com.duarf.engine.ml.LinearClassifier.fromBytes(it) }
    } catch (_: Exception) {
        null
    }

    override val isModelLoaded: Boolean
        get() = classifier != null

    override val modelVersion: Int?
        get() = classifier?.formatVersion

    val languagePackCount: Int
        get() = packs.languages.size

    val effectiveEngineVersion: String = if (classifier != null) {
        "1.0.0-model-v${classifier.formatVersion}"
    } else {
        engineVersion
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

            // 6. ML model prediction (§9)
            // Script & Marathi gating (§8): gate model off (m' = 0) if predominant script is untrained or language is Marathi
            val isUntrained = gateUntrainedScripts && (
                com.duarf.engine.normalize.LanguageScriptDetector.isUntrainedScript(message.text) ||
                com.duarf.engine.normalize.LanguageScriptDetector.isMarathi(message.text)
            )

            if (isUntrained) {
                eventSink?.invoke(EVENT_MODEL_UNTRAINED_SCRIPT_RULES_ONLY)
            }

            val modelPrediction = if (!isUntrained) {
                try {
                    classifier?.let { cls ->
                        val featurized = featurizer.featurize(message, normalized, extracted)
                        cls.predict(featurized)
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            // 7. Score fusion (§10)
            val hasCallbackAsk = signalEngine.hasCallbackAsk(normalized, extracted)
            val fusion = ScoreFusion.fuse(
                signals = allSignals,
                dampeners = dampeners,
                combos = combos,
                modelProbability = modelPrediction?.probability,
                sensitivity = sensitivity,
                isSms = message.app.isSms,
                hasCallbackAsk = hasCallbackAsk
            )

            // 8. Explanations and highlights (§11.4: model highlights added when m' > 0.2)
            val modelHighlights = if (fusion.mPrime > 0.2) modelPrediction?.highlights ?: emptyList() else emptyList()
            val (reasons, highlights) = ExplanationEngine.generateReasonsAndHighlights(
                signals = allSignals,
                topCombo = fusion.topCombo,
                modelHighlights = modelHighlights,
                originalText = message.text
            )

            Verdict(
                level = fusion.level,
                score = fusion.score,
                ruleScore = fusion.ruleScore,
                modelProbability = modelPrediction?.probability,
                category = fusion.category,
                reasons = reasons,
                highlights = highlights,
                engineVersion = effectiveEngineVersion
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
                engineVersion = effectiveEngineVersion
            )
        }
    }

    companion object {
        const val EVENT_MODEL_UNTRAINED_SCRIPT_RULES_ONLY = 304

        fun fromPackSource(
            packSource: PackSource,
            engineVersion: String = "1.0.0-rules",
            gateUntrainedScripts: Boolean = true,
            eventSink: ((Int) -> Unit)? = null
        ): DefaultScamEngine {
            val loadedPacks = PackLoader.load(packSource)
            return DefaultScamEngine(loadedPacks, engineVersion, gateUntrainedScripts, eventSink)
        }
    }
}
