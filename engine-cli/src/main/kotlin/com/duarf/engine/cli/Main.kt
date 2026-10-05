package com.duarf.engine.cli

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.EntityExtractor
import com.duarf.engine.extract.PublicSuffixList
import com.duarf.engine.ml.Featurizer
import com.duarf.engine.model.*
import com.duarf.engine.normalize.TextNormalizer
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.LoadedPacks
import com.duarf.engine.pack.PackLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

@Serializable
data class CorpusRow(
    val id: String = "",
    val text: String,
    val label: String, // "scam" or "benign"
    val category: String = "OTHER_SUSPICIOUS",
    val lang: String = "en",
    val sender_kind: String = "NUMBER_ONLY",
    val is_group: Boolean = false,
    val origin: String = "synthetic",
    val group_id: String = "",
    val notes: String = ""
)

@Serializable
data class LanguageMetrics(
    val lang: String,
    val total: Int,
    val scam: Int,
    val benign: Int,
    val dangerPrecision: Double,
    val recall: Double,
    val benignToDangerPct: Double,
    val benignToCautionPct: Double,
    val passedGates: Boolean = true,
    val failureReasons: List<String> = emptyList()
)

@Serializable
data class AdversarialMetrics(
    val total: Int,
    val scam: Int,
    val benign: Int,
    val scamRecall: Double? = null,
    val fpDanger: Int = 0,
    val fpCaution: Int = 0,
    val benignToDangerPct: Double = 0.0,
    val benignToCautionPct: Double = 0.0,
    val precision: Double? = null
)

@Serializable
data class EvaluationMetrics(
    val totalRows: Int,
    val totalScam: Int,
    val totalBenign: Int,
    val truePositivesDanger: Int,
    val truePositivesCaution: Int,
    val falseNegativesNone: Int,
    val trueNegativesNone: Int,
    val falsePositivesCaution: Int,
    val falsePositivesDanger: Int,
    val dangerPrecision: Double,
    val cautionOrAboveRecall: Double,
    val benignRaisedToDangerPercent: Double,
    val benignRaisedToCautionOrAbovePercent: Double,
    val passedTier1Gates: Boolean,
    val perLanguage: List<LanguageMetrics> = emptyList(),
    val adversarialMetrics: AdversarialMetrics? = null,
    val adversarialRecall: Double? = null,
    val rulesOnlyRecall: Double? = null,
    val rulesPlusMlRecall: Double? = null
)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(1)
    }

    when (args[0]) {
        "explain" -> runExplain(args.drop(1))
        "eval" -> runEval(args.drop(1))
        "featurize" -> runFeaturize(args.drop(1))
        else -> {
            System.err.println("Unknown command: ${args[0]}")
            printUsage()
            exitProcess(1)
        }
    }
}

private fun printUsage() {
    println("""
        DUARF Engine CLI
        Usage:
          engine-cli explain --text "<message text>" [--packs <path>]
          engine-cli eval --in <corpus.jsonl> [--out <report.json>] [--packs <path>]
          engine-cli featurize --in <dataset.jsonl> --out <dataset.svm> [--packs <path>]
    """.trimIndent())
}

private fun resolveFile(path: String): File {
    val f = File(path)
    if (f.exists()) return f
    val parentF = File("..", path)
    if (parentF.exists()) return parentF
    return f
}

private fun resolvePacksDir(args: List<String>): File {
    val packsIdx = args.indexOf("--packs")
    if (packsIdx >= 0 && packsIdx + 1 < args.size) {
        return resolveFile(args[packsIdx + 1])
    }
    val candidates = listOf(File("packs"), File("../packs"), File("../../packs"))
    return candidates.firstOrNull { it.exists() && File(it, "rules.json").exists() }
        ?: File("packs")
}

private fun runExplain(args: List<String>) {
    val textIdx = args.indexOf("--text")
    if (textIdx < 0 || textIdx + 1 >= args.size) {
        System.err.println("Error: --text parameter required")
        exitProcess(1)
    }
    val text = args[textIdx + 1]
    val packsDir = resolvePacksDir(args)

    val engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))
    val msg = IncomingMessage(
        fingerprint = "cli-explain",
        source = SourceKind.NOTIFICATION,
        app = SourceApp.WHATSAPP,
        conversationKey = "conv-cli",
        senderDisplay = "+919876543210",
        senderKind = SenderKind.NUMBER_ONLY,
        senderCountryCode = "+91",
        isGroup = false,
        text = text,
        attachmentHint = null,
        receivedAtMillis = System.currentTimeMillis()
    )

    val verdict = engine.analyze(msg)

    println("================ VERDICT ================")
    println("Level:       ${verdict.level}")
    println("Score:       ${verdict.score}")
    println("Rule Score:  ${verdict.ruleScore}")
    println("Model Prob:  ${verdict.modelProbability ?: "N/A"}")
    println("Category:    ${verdict.category}")
    println("Version:     ${verdict.engineVersion}")
    println("---------------- REASONS ----------------")
    verdict.reasons.forEach { r ->
        println("* [${r.signalId}] ${r.titleKey}: ${r.detailKey} (evidence: ${r.evidence})")
    }
    println("--------------- HIGHLIGHTS --------------")
    verdict.highlights.forEach {
        val spanText = if (it.start in text.indices && it.end <= text.length) text.substring(it.start, it.end) else "<out-of-bounds>"
        println("[${it.start}..${it.end}]: \"$spanText\"")
    }
    println("=========================================")
}

private fun runFeaturize(args: List<String>) {
    val inIdx = args.indexOf("--in")
    val outIdx = args.indexOf("--out")
    if (inIdx < 0 || inIdx + 1 >= args.size || outIdx < 0 || outIdx + 1 >= args.size) {
        System.err.println("Error: --in and --out parameters required")
        exitProcess(1)
    }

    val inputFile = resolveFile(args[inIdx + 1])
    val outputFile = File(args[outIdx + 1])
    val packsDir = resolvePacksDir(args)

    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
        exitProcess(1)
    }

    val packs = PackLoader.load(FilePackSource(packsDir))
    val psl = if (packs.pslLines.isNotEmpty()) PublicSuffixList.parseFromLines(packs.pslLines.asSequence()) else PublicSuffixList()
    val entityExtractor = EntityExtractor(psl, packs.brands, packs.upiHandles)
    val featurizer = Featurizer()

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    outputFile.parentFile?.mkdirs()

    var count = 0
    outputFile.bufferedWriter().use { writer ->
        inputFile.forEachLine { line ->
            if (line.isNotBlank() && !line.trim().startsWith("#")) {
                val row = try {
                    json.decodeFromString<CorpusRow>(line)
                } catch (_: Exception) {
                    null
                }
                if (row != null) {
                    val sKind = if (row.sender_kind.uppercase() == "NAMED") SenderKind.NAMED else SenderKind.NUMBER_ONLY
                    val msg = IncomingMessage(
                        fingerprint = row.id,
                        source = SourceKind.NOTIFICATION,
                        app = SourceApp.WHATSAPP,
                        conversationKey = "conv_${row.id}",
                        senderDisplay = if (sKind == SenderKind.NAMED) "BANK" else "+919876543210",
                        senderKind = sKind,
                        senderCountryCode = "+91",
                        isGroup = row.is_group,
                        text = row.text,
                        attachmentHint = null,
                        receivedAtMillis = System.currentTimeMillis()
                    )

                    val normalized = TextNormalizer.normalize(msg.text)
                    val extracted = entityExtractor.extract(normalized)
                    val feat = featurizer.featurize(msg, normalized, extracted)

                    val label = if (row.label.lowercase() == "scam") "+1" else "-1"
                    val sb = StringBuilder()
                    sb.append(label)
                    val l2Str = String.format(Locale.US, "%.6f", feat.l2Value)
                    for (idx in feat.activeIndices) {
                        // 1-based index for standard LIBSVM
                        sb.append(' ').append(idx + 1).append(':').append(l2Str)
                    }
                    writer.write(sb.toString())
                    writer.newLine()
                    count++
                }
            }
        }
    }
    println("Featurized $count rows from ${inputFile.name} to ${outputFile.name}")
}

private fun runEval(args: List<String>) {
    val inIdx = args.indexOf("--in")
    if (inIdx < 0 || inIdx + 1 >= args.size) {
        System.err.println("Error: --in parameter required")
        exitProcess(1)
    }
    val inputFile = resolveFile(args[inIdx + 1])
    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
        exitProcess(1)
    }

    val outIdx = args.indexOf("--out")
    val outFile = if (outIdx >= 0 && outIdx + 1 < args.size) resolveFile(args[outIdx + 1]) else null

    val packsDir = resolvePacksDir(args)
    val loadedPacks = PackLoader.load(FilePackSource(packsDir))
    val engine = DefaultScamEngine(loadedPacks)

    // Also build a rules-only engine for comparison
    val rulesOnlyEngine = DefaultScamEngine(loadedPacks.copy(modelBytes = null))

    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    data class StatCounters(
        var total: Int = 0,
        var scam: Int = 0,
        var benign: Int = 0,
        var tpDanger: Int = 0,
        var tpCaution: Int = 0,
        var fnNone: Int = 0,
        var tnNone: Int = 0,
        var fpCaution: Int = 0,
        var fpDanger: Int = 0
    )

    val overall = StatCounters()
    val rulesOnlyCounters = StatCounters()
    val perLangCounters = HashMap<String, StatCounters>()
    val advCounters = StatCounters()
    val fnGroupIds = HashMap<String, Int>()

    val lines = inputFile.readLines()
    for (line in lines) {
        if (line.isBlank() || line.trim().startsWith("#")) continue
        val row = try {
            json.decodeFromString<CorpusRow>(line)
        } catch (_: Exception) {
            continue
        }

        val isScam = row.label.lowercase() == "scam"
        val lang = row.lang
        val isAdv = row.notes == "adversarial"

        val langStat = perLangCounters.getOrPut(lang) { StatCounters() }

        val sKind = when (row.sender_kind.uppercase()) {
            "NAMED" -> SenderKind.NAMED
            "UNKNOWN" -> SenderKind.UNKNOWN
            else -> SenderKind.NUMBER_ONLY
        }

        val msg = IncomingMessage(
            fingerprint = "eval-${row.id.ifEmpty { overall.total.toString() }}",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-${overall.total}",
            senderDisplay = if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
            senderKind = sKind,
            senderCountryCode = "+91",
            isGroup = row.is_group,
            text = row.text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        // 1. Evaluate with full engine (rules + ML)
        val verdict = engine.analyze(msg)

        fun record(c: StatCounters, v: AlertLevel) {
            c.total++
            if (isScam) {
                c.scam++
                when (v) {
                    AlertLevel.DANGER -> c.tpDanger++
                    AlertLevel.CAUTION -> c.tpCaution++
                    AlertLevel.NONE -> {
                        c.fnNone++
                        if (c == overall) {
                            fnGroupIds[row.group_id] = (fnGroupIds[row.group_id] ?: 0) + 1
                        }
                    }
                }
            } else {
                c.benign++
                when (v) {
                    AlertLevel.NONE -> c.tnNone++
                    AlertLevel.CAUTION -> c.fpCaution++
                    AlertLevel.DANGER -> c.fpDanger++
                }
            }
        }

        record(overall, verdict.level)
        record(langStat, verdict.level)
        if (isAdv) record(advCounters, verdict.level)

        // 2. Evaluate with rules-only engine
        val rulesVerdict = rulesOnlyEngine.analyze(msg)
        record(rulesOnlyCounters, rulesVerdict.level)
    }

    fun calcMetrics(c: StatCounters): Triple<Double, Double, Pair<Double, Double>> {
        val prec = if (c.tpDanger + c.fpDanger > 0) c.tpDanger.toDouble() / (c.tpDanger + c.fpDanger) else 1.0
        val rec = if (c.scam > 0) (c.tpDanger + c.tpCaution).toDouble() / c.scam else 0.0
        val bDanger = if (c.benign > 0) (c.fpDanger.toDouble() / c.benign) * 100.0 else 0.0
        val bCaution = if (c.benign > 0) ((c.fpCaution + c.fpDanger).toDouble() / c.benign) * 100.0 else 0.0
        return Triple(prec, rec, Pair(bDanger, bCaution))
    }

    val (dangerPrec, recall, benignPcts) = calcMetrics(overall)
    val (bToDanger, bToCaution) = benignPcts

    val (rulesPrec, rulesRec, _) = calcMetrics(rulesOnlyCounters)

    // Tier 1 Gates (§16.2):
    // Danger precision >= 0.97, Recall at Caution or above >= 0.90, Benign->Danger <= 0.3%, Benign->Caution <= 2.0%
    val overallPassed = dangerPrec >= 0.97 && recall >= 0.90 && bToDanger <= 0.3 && bToCaution <= 2.0

    val tier1Langs = setOf("en", "hi", "hi-Latn")
    var anyTier1LangFailed = false

    val langList = ArrayList<LanguageMetrics>()
    for ((l, c) in perLangCounters.entries.sortedBy { it.key }) {
        val (lp, lr, lb) = calcMetrics(c)
        val dPrec = Math.round(lp * 1000.0) / 1000.0
        val rec = Math.round(lr * 1000.0) / 1000.0
        val bDanger = Math.round(lb.first * 100.0) / 100.0
        val bCaution = Math.round(lb.second * 100.0) / 100.0

        val failures = ArrayList<String>()
        if (tier1Langs.contains(l)) {
            if (c.scam > 0 && dPrec < 0.97) failures.add("Danger Precision $dPrec < 0.97")
            if (c.scam > 0 && rec < 0.90) failures.add("Caution+ Recall $rec < 0.90")
            if (c.benign > 0 && bDanger > 0.3) failures.add("Benign->Danger $bDanger% > 0.3%")
            if (c.benign > 0 && bCaution > 2.0) failures.add("Benign->Caution $bCaution% > 2.0%")
        }
        val langPassed = failures.isEmpty()
        if (!langPassed && tier1Langs.contains(l)) {
            anyTier1LangFailed = true
        }

        langList.add(
            LanguageMetrics(
                lang = l,
                total = c.total,
                scam = c.scam,
                benign = c.benign,
                dangerPrecision = dPrec,
                recall = rec,
                benignToDangerPct = bDanger,
                benignToCautionPct = bCaution,
                passedGates = langPassed,
                failureReasons = failures
            )
        )
    }

    val passedTier1 = overallPassed && !anyTier1LangFailed

    val advScamRec = if (advCounters.scam > 0) (advCounters.tpDanger + advCounters.tpCaution).toDouble() / advCounters.scam else null
    val advPrec = if (advCounters.tpDanger + advCounters.fpDanger > 0) advCounters.tpDanger.toDouble() / (advCounters.tpDanger + advCounters.fpDanger) else 1.0
    val advBDanger = if (advCounters.benign > 0) (advCounters.fpDanger.toDouble() / advCounters.benign) * 100.0 else 0.0
    val advBCaution = if (advCounters.benign > 0) ((advCounters.fpCaution + advCounters.fpDanger).toDouble() / advCounters.benign) * 100.0 else 0.0

    val advMetrics = AdversarialMetrics(
        total = advCounters.total,
        scam = advCounters.scam,
        benign = advCounters.benign,
        scamRecall = advScamRec?.let { Math.round(it * 1000.0) / 1000.0 },
        fpDanger = advCounters.fpDanger,
        fpCaution = advCounters.fpCaution,
        benignToDangerPct = Math.round(advBDanger * 100.0) / 100.0,
        benignToCautionPct = Math.round(advBCaution * 100.0) / 100.0,
        precision = Math.round(advPrec * 1000.0) / 1000.0
    )

    val evalMetrics = EvaluationMetrics(
        totalRows = overall.total,
        totalScam = overall.scam,
        totalBenign = overall.benign,
        truePositivesDanger = overall.tpDanger,
        truePositivesCaution = overall.tpCaution,
        falseNegativesNone = overall.fnNone,
        trueNegativesNone = overall.tnNone,
        falsePositivesCaution = overall.fpCaution,
        falsePositivesDanger = overall.fpDanger,
        dangerPrecision = Math.round(dangerPrec * 1000.0) / 1000.0,
        cautionOrAboveRecall = Math.round(recall * 1000.0) / 1000.0,
        benignRaisedToDangerPercent = Math.round(bToDanger * 100.0) / 100.0,
        benignRaisedToCautionOrAbovePercent = Math.round(bToCaution * 100.0) / 100.0,
        passedTier1Gates = passedTier1,
        perLanguage = langList,
        adversarialMetrics = advMetrics,
        adversarialRecall = advMetrics.scamRecall,
        rulesOnlyRecall = Math.round(rulesRec * 1000.0) / 1000.0,
        rulesPlusMlRecall = Math.round(recall * 1000.0) / 1000.0
    )

    println("""
        ==================== EVALUATION REPORT (TIER 1 GATES) ====================
        Total Rows:           ${overall.total} (Scam: ${overall.scam}, Benign: ${overall.benign})
        -------------------------------------------------------------------------
        Scam - DANGER (TP):   ${overall.tpDanger}
        Scam - CAUTION (TP):  ${overall.tpCaution}
        Scam - NONE (FN):     ${overall.fnNone}
        -------------------------------------------------------------------------
        Benign - NONE (TN):   ${overall.tnNone}
        Benign - CAUTION (FP):${overall.fpCaution}
        Benign - DANGER (FP): ${overall.fpDanger}
        -------------------------------------------------------------------------
        Overall Metrics:
          Danger Precision:   ${evalMetrics.dangerPrecision} (Target: >= 0.97)
          Caution+ Recall:    ${evalMetrics.cautionOrAboveRecall} (Target: >= 0.90)
          Benign -> Danger:   ${evalMetrics.benignRaisedToDangerPercent}% (Target: <= 0.3%)
          Benign -> Caution+: ${evalMetrics.benignRaisedToCautionOrAbovePercent}% (Target: <= 2.0%)
        -------------------------------------------------------------------------
        PER-LANGUAGE BREAKDOWN (§16.2 Tier 1 Gates):
    """.trimIndent())

    for (lm in langList) {
        val status = if (lm.passedGates) "[PASS]" else "[FAIL: ${lm.failureReasons.joinToString(", ")}]"
        println("  Language [${lm.lang.padEnd(7)}]: Total=${lm.total}, Scam=${lm.scam}, Benign=${lm.benign} | Prec=${lm.dangerPrecision}, Rec=${lm.recall}, B->Danger=${lm.benignToDangerPct}%, B->Caution=${lm.benignToCautionPct}% $status")
    }

    if (fnGroupIds.isNotEmpty()) {
        println("  False Negatives by template (group_id): $fnGroupIds")
    }

    if (advMetrics.total > 0) {
        println("""
        -------------------------------------------------------------------------
        ADVERSARIAL EVALUATION (§16.2 / Point 5):
          Total Rows:         ${advMetrics.total} (Scam: ${advMetrics.scam}, Benign: ${advMetrics.benign})
          Scam Recall:        ${advMetrics.scamRecall ?: 1.0}
          Benign False Pos:   Danger=${advMetrics.fpDanger} (${advMetrics.benignToDangerPct}%), Caution=${advMetrics.fpCaution} (${advMetrics.benignToCautionPct}%)
          Adversarial Prec:   ${advMetrics.precision ?: 1.0}
        """.trimIndent())
    }

    println("""
        -------------------------------------------------------------------------
        RECALL COMPARISON:
          Rules-only Recall:  ${evalMetrics.rulesOnlyRecall} (Danger Prec: ${Math.round(rulesPrec * 1000.0) / 1000.0})
          Rules + ML Recall:  ${evalMetrics.rulesPlusMlRecall} (Danger Prec: ${evalMetrics.dangerPrecision})
        -------------------------------------------------------------------------
        TIER 1 GATES PASSED:  ${if (passedTier1) "YES [PASS]" else "NO [FAIL]"}
        =========================================================================
    """.trimIndent())

    if (outFile != null) {
        outFile.parentFile?.mkdirs()
        outFile.writeText(json.encodeToString(evalMetrics))
        println("Report written to: ${outFile.absolutePath}")
    }

    if (!passedTier1) {
        exitProcess(2)
    }
}
