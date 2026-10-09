// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.cli

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.extract.DltHeaderParser
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
    val notes: String = "",
    val app: String = "WHATSAPP",
    val sender_display: String? = null
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
    val intendedCautionCount: Int = 0,
    val benignToCautionExclIntendedPct: Double = benignToCautionPct,
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
    val intendedCautionCount: Int = 0,
    val intendedCautionTemplates: Map<String, Int> = emptyMap(),
    val benignRaisedToCautionExclIntendedPercent: Double = benignRaisedToCautionOrAbovePercent,
    val passedTier1Gates: Boolean,
    val passedTier2Gates: Boolean = true,
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
          engine-cli explain (--text "<message text>" | --row "<json row>" | --in <dataset.jsonl> --id <id>) [--app <WHATSAPP|SMS>] [--sender <sender>] [--sender-kind <kind>] [--packs <path>]
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
    val rowIdx = args.indexOf("--row")
    val idIdx = args.indexOf("--id")
    val inIdx = args.indexOf("--in")
    val textIdx = args.indexOf("--text")

    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    var parsedRow: CorpusRow? = null

    if (rowIdx >= 0 && rowIdx + 1 < args.size) {
        val rowStr = args[rowIdx + 1]
        parsedRow = try { json.decodeFromString<CorpusRow>(rowStr) } catch (_: Exception) { null }
    } else if (inIdx >= 0 && inIdx + 1 < args.size && idIdx >= 0 && idIdx + 1 < args.size) {
        val inputFile = resolveFile(args[inIdx + 1])
        val targetId = args[idIdx + 1]
        if (inputFile.exists()) {
            for (line in inputFile.readLines()) {
                if (line.isBlank() || line.trim().startsWith("#")) continue
                try {
                    val r = json.decodeFromString<CorpusRow>(line)
                    if (r.id == targetId) {
                        parsedRow = r
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    } else if (textIdx >= 0 && textIdx + 1 < args.size) {
        val rawCandidate = args[textIdx + 1].trim()
        if (rawCandidate.startsWith("{") && rawCandidate.endsWith("}") && rawCandidate.contains("\"text\"")) {
            parsedRow = try { json.decodeFromString<CorpusRow>(rawCandidate) } catch (_: Exception) { null }
        }
    }

    val text = when {
        textIdx >= 0 && textIdx + 1 < args.size && parsedRow == null -> args[textIdx + 1]
        parsedRow != null -> parsedRow.text
        textIdx >= 0 && textIdx + 1 < args.size -> args[textIdx + 1]
        else -> {
            System.err.println("Error: --text, --row, or --in + --id parameter required")
            exitProcess(1)
        }
    }
    val packsDir = resolvePacksDir(args)

    val engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))

    val appIdx = args.indexOf("--app")
    val appStr = when {
        appIdx >= 0 && appIdx + 1 < args.size -> args[appIdx + 1]
        parsedRow != null -> parsedRow.app
        else -> "WHATSAPP"
    }
    val sourceApp = when (appStr.uppercase()) {
        "SMS", "SMS_GOOGLE_MESSAGES" -> SourceApp.SMS_GOOGLE_MESSAGES
        "SMS_SAMSUNG_MESSAGES" -> SourceApp.SMS_SAMSUNG_MESSAGES
        "SMS_GENERIC" -> SourceApp.SMS_GENERIC
        "WHATSAPP_BUSINESS" -> SourceApp.WHATSAPP_BUSINESS
        else -> SourceApp.WHATSAPP
    }

    val senderIdx = args.indexOf("--sender")
    val senderArg = when {
        senderIdx >= 0 && senderIdx + 1 < args.size -> args[senderIdx + 1]
        parsedRow?.sender_display != null -> parsedRow.sender_display
        parsedRow?.sender_kind?.uppercase() == "DLT_HEADER" -> "VM-SBIBNK-T"
        else -> null
    }

    val skIdx = args.indexOf("--sender-kind")
    val sKindArg = when {
        skIdx >= 0 && skIdx + 1 < args.size -> args[skIdx + 1]
        parsedRow != null -> parsedRow.sender_kind
        else -> null
    }
    val isGroup = parsedRow?.is_group ?: false

    val senderDisplay: String
    val sKind: SenderKind
    val senderCountryCode: String?
    val dltHeaderPrefix: String?
    val dltHeaderBrand: String?
    val dltHeaderSuffix: String?

    if (sourceApp.isSms) {
        val sDisp = senderArg ?: if (sKindArg?.equals("NAMED", ignoreCase = true) == true) "BANK" else "+919876543210"
        val parsed = DltHeaderParser.parse(sDisp)
        senderDisplay = sDisp
        sKind = if (sKindArg != null) {
            when (sKindArg.uppercase()) {
                "DLT_HEADER" -> SenderKind.DLT_HEADER
                "PERSONAL_NUMBER" -> SenderKind.PERSONAL_NUMBER
                "SHORT_CODE" -> SenderKind.SHORT_CODE
                "SAVED_CONTACT", "NAMED" -> SenderKind.SAVED_CONTACT
                "NUMBER_ONLY" -> parsed.senderKind
                "UNKNOWN" -> SenderKind.UNKNOWN
                else -> parsed.senderKind
            }
        } else {
            parsed.senderKind
        }
        senderCountryCode = parsed.countryCode ?: "+91"
        dltHeaderPrefix = parsed.dltPrefix
        dltHeaderBrand = parsed.dltBrand
        dltHeaderSuffix = parsed.dltSuffix
    } else {
        val isNamed = sKindArg?.equals("NAMED", ignoreCase = true) == true
        sKind = if (isNamed) SenderKind.NAMED else SenderKind.NUMBER_ONLY
        senderDisplay = senderArg ?: if (isNamed) "Contact" else "+919876543210"
        senderCountryCode = "+91"
        dltHeaderPrefix = null
        dltHeaderBrand = null
        dltHeaderSuffix = null
    }

    val msg = IncomingMessage(
        fingerprint = parsedRow?.id?.ifEmpty { "cli-explain" } ?: "cli-explain",
        source = SourceKind.NOTIFICATION,
        app = sourceApp,
        conversationKey = "conv-cli",
        senderDisplay = senderDisplay,
        senderKind = sKind,
        senderCountryCode = senderCountryCode,
        isGroup = isGroup,
        text = text,
        attachmentHint = null,
        receivedAtMillis = System.currentTimeMillis(),
        dltHeaderPrefix = dltHeaderPrefix,
        dltHeaderBrand = dltHeaderBrand,
        dltHeaderSuffix = dltHeaderSuffix
    )

    val verdict = engine.analyze(msg)

    val displayCategory = when {
        verdict.score == 0.0 && verdict.level == AlertLevel.NONE -> "NONE"
        verdict.level == AlertLevel.NONE && verdict.category == ScamCategory.OTHER_SUSPICIOUS -> "NONE"
        else -> verdict.category.name
    }

    println("================ VERDICT ================")
    println("Level:       ${verdict.level}")
    println("Score:       ${verdict.score}")
    println("Rule Score:  ${verdict.ruleScore}")
    println("Model Prob:  ${verdict.modelProbability ?: "N/A"}")
    println("Category:    $displayCategory")
    println("Version:     ${verdict.engineVersion}")
    if (sourceApp.isSms || senderArg != null) {
        println("---------------- SENDER -----------------")
        println("App:         $sourceApp")
        println("Display:     $senderDisplay")
        println("Kind:        $sKind")
        if (dltHeaderBrand != null || dltHeaderSuffix != null) {
            println("DLT Header:  prefix=$dltHeaderPrefix, brand=$dltHeaderBrand, suffix=$dltHeaderSuffix")
        }
    }
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
    val gateUntrainedIdx = args.indexOf("--gate-untrained-scripts")
    val gateUntrained = if (gateUntrainedIdx >= 0 && gateUntrainedIdx + 1 < args.size) {
        args[gateUntrainedIdx + 1].toBoolean()
    } else {
        true
    }
    val engine = DefaultScamEngine(loadedPacks, gateUntrainedScripts = gateUntrained)

    // Also build a rules-only engine for comparison
    val rulesOnlyEngine = DefaultScamEngine(loadedPacks.copy(modelBytes = null), gateUntrainedScripts = gateUntrained)

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
        var fpDanger: Int = 0,
        var intendedCaution: Int = 0
    )

    val overall = StatCounters()
    val rulesOnlyCounters = StatCounters()
    val perLangCounters = HashMap<String, StatCounters>()
    val advCounters = StatCounters()
    val fnGroupIds = HashMap<String, Int>()
    val fpGroupIds = HashMap<String, Int>()
    val intendedCautionGroupIds = HashMap<String, Int>()

    val lines = inputFile.readLines()
    for (line in lines) {
        if (line.isBlank() || line.trim().startsWith("#")) continue
        val row = try {
            json.decodeFromString<CorpusRow>(line)
        } catch (_: Exception) {
            continue
        }

        if (row.label.lowercase() == "context_only") continue

        val isScam = row.label.lowercase() == "scam"
        val lang = row.lang
        val isAdv = row.notes == "adversarial"

        val langStat = perLangCounters.getOrPut(lang) { StatCounters() }

        val sourceApp = when (row.app.uppercase()) {
            "SMS", "SMS_GOOGLE_MESSAGES" -> SourceApp.SMS_GOOGLE_MESSAGES
            "SMS_SAMSUNG_MESSAGES" -> SourceApp.SMS_SAMSUNG_MESSAGES
            "SMS_GENERIC" -> SourceApp.SMS_GENERIC
            "WHATSAPP_BUSINESS" -> SourceApp.WHATSAPP_BUSINESS
            else -> SourceApp.WHATSAPP
        }

        val msg = if (sourceApp.isSms) {
            val senderDisplay = row.sender_display ?: if (row.sender_kind.uppercase() == "DLT_HEADER") "VM-SBIBNK-T" else "+919876543210"
            val parsedSender = DltHeaderParser.parse(senderDisplay)
            val sKind = when (row.sender_kind.uppercase()) {
                "DLT_HEADER" -> SenderKind.DLT_HEADER
                "PERSONAL_NUMBER" -> SenderKind.PERSONAL_NUMBER
                "SHORT_CODE" -> SenderKind.SHORT_CODE
                "SAVED_CONTACT", "NAMED" -> SenderKind.SAVED_CONTACT
                "NUMBER_ONLY" -> parsedSender.senderKind
                "UNKNOWN" -> SenderKind.UNKNOWN
                else -> parsedSender.senderKind
            }
            IncomingMessage(
                fingerprint = "eval-${row.id.ifEmpty { overall.total.toString() }}",
                source = SourceKind.NOTIFICATION,
                app = sourceApp,
                conversationKey = "conv-${overall.total}",
                senderDisplay = senderDisplay,
                senderKind = sKind,
                senderCountryCode = parsedSender.countryCode ?: "+91",
                isGroup = row.is_group,
                text = row.text,
                attachmentHint = null,
                receivedAtMillis = System.currentTimeMillis(),
                dltHeaderPrefix = parsedSender.dltPrefix,
                dltHeaderBrand = parsedSender.dltBrand,
                dltHeaderSuffix = parsedSender.dltSuffix
            )
        } else {
            val sKind = when (row.sender_kind.uppercase()) {
                "NAMED" -> SenderKind.NAMED
                "UNKNOWN" -> SenderKind.UNKNOWN
                else -> SenderKind.NUMBER_ONLY
            }
            IncomingMessage(
                fingerprint = "eval-${row.id.ifEmpty { overall.total.toString() }}",
                source = SourceKind.NOTIFICATION,
                app = SourceApp.WHATSAPP,
                conversationKey = "conv-${overall.total}",
                senderDisplay = row.sender_display ?: if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
                senderKind = sKind,
                senderCountryCode = "+91",
                isGroup = row.is_group,
                text = row.text,
                attachmentHint = null,
                receivedAtMillis = System.currentTimeMillis()
            )
        }

        // 1. Evaluate with full engine (rules + ML)
        val verdict = engine.analyze(msg)

        val isIntendedCaution = !isScam && verdict.level == AlertLevel.CAUTION &&
                verdict.reasons.any { it.signalId == "L05" } &&
                (row.category.uppercase() in setOf("DELIVERY", "DELIVERY_COURIER", "ECOMMERCE", "PROMO") ||
                 verdict.reasons.any { it.signalId in setOf("P11", "P05", "P06", "P07", "P08", "P09") } ||
                 row.group_id.contains("delivery") || row.group_id.contains("promo") || row.group_id.contains("ecommerce"))

        fun record(c: StatCounters, v: AlertLevel, isIntended: Boolean = false) {
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
                    AlertLevel.CAUTION -> {
                        c.fpCaution++
                        if (isIntended) {
                            c.intendedCaution++
                            if (c == overall) {
                                intendedCautionGroupIds[row.group_id] = (intendedCautionGroupIds[row.group_id] ?: 0) + 1
                            }
                        }
                        if (c == overall) fpGroupIds[row.group_id] = (fpGroupIds[row.group_id] ?: 0) + 1
                    }
                    AlertLevel.DANGER -> {
                        c.fpDanger++
                        if (c == overall) fpGroupIds[row.group_id] = (fpGroupIds[row.group_id] ?: 0) + 1
                    }
                }
            }
        }

        record(overall, verdict.level, isIntendedCaution)
        record(langStat, verdict.level, isIntendedCaution)
        if (isAdv) record(advCounters, verdict.level, isIntendedCaution)

        // 2. Evaluate with rules-only engine
        val rulesVerdict = rulesOnlyEngine.analyze(msg)
        record(rulesOnlyCounters, rulesVerdict.level, false)
    }

    fun calcMetrics(c: StatCounters): Triple<Double, Double, Triple<Double, Double, Double>> {
        val prec = if (c.tpDanger + c.fpDanger > 0) c.tpDanger.toDouble() / (c.tpDanger + c.fpDanger) else 1.0
        val rec = if (c.scam > 0) (c.tpDanger + c.tpCaution).toDouble() / c.scam else 0.0
        val bDanger = if (c.benign > 0) (c.fpDanger.toDouble() / c.benign) * 100.0 else 0.0
        val bCaution = if (c.benign > 0) ((c.fpCaution + c.fpDanger).toDouble() / c.benign) * 100.0 else 0.0
        val bCautionExcl = if (c.benign > 0) (((c.fpCaution - c.intendedCaution) + c.fpDanger).toDouble() / c.benign) * 100.0 else 0.0
        return Triple(prec, rec, Triple(bDanger, bCaution, bCautionExcl))
    }

    val (dangerPrec, recall, benignTriples) = calcMetrics(overall)
    val (bToDanger, bToCaution, bToCautionExclIntended) = benignTriples

    val (rulesPrec, rulesRec, _) = calcMetrics(rulesOnlyCounters)

    val tier1Langs = setOf("en", "hi", "hi-Latn")
    val tier2Langs = setOf("bn", "mr", "te", "ta", "or")
    val tier3Langs = setOf("gu", "kn", "ml", "pa")
    val hasTier1 = perLangCounters.keys.any { tier1Langs.contains(it) }
    val isTier2Only = perLangCounters.keys.isNotEmpty() && perLangCounters.keys.all { tier2Langs.contains(it) }
    val isTier3Only = perLangCounters.keys.isNotEmpty() && perLangCounters.keys.all { tier3Langs.contains(it) }

    // Targets (§16.2):
    // Tier 1: Danger precision >= 0.97, Caution+ recall >= 0.90, Benign->Danger <= 0.3%, Benign->Caution <= 2.0%
    // Tier 2: Danger precision >= 0.95, Caution+ recall >= 0.80, Benign->Danger <= 0.5%, Benign->Caution <= 3.0%
    // Tier 3: Ungated / Reported only
    val overallPassed = when {
        isTier3Only -> true
        isTier2Only -> dangerPrec >= 0.95 && recall >= 0.80 && bToDanger <= 0.5 && bToCautionExclIntended <= 3.0
        else -> dangerPrec >= 0.97 && recall >= 0.90 && bToDanger <= 0.3 && bToCautionExclIntended <= 2.0
    }

    var anyTier1LangFailed = false
    var anyTier2LangFailed = false

    val langList = ArrayList<LanguageMetrics>()
    for ((l, c) in perLangCounters.entries.sortedBy { it.key }) {
        val (lp, lr, lb) = calcMetrics(c)
        val dPrec = Math.round(lp * 1000.0) / 1000.0
        val rec = Math.round(lr * 1000.0) / 1000.0
        val bDanger = Math.round(lb.first * 100.0) / 100.0
        val bCaution = Math.round(lb.second * 100.0) / 100.0
        val bCautionExcl = Math.round(lb.third * 100.0) / 100.0

        val failures = ArrayList<String>()
        if (tier1Langs.contains(l)) {
            if (c.scam > 0 && dPrec < 0.97) failures.add("Danger Precision $dPrec < 0.97")
            if (c.scam > 0 && rec < 0.90) failures.add("Caution+ Recall $rec < 0.90")
            if (c.benign > 0 && bDanger > 0.3) failures.add("Benign->Danger $bDanger% > 0.3%")
            if (c.benign > 0 && bCautionExcl > 2.0) failures.add("Benign->Caution (excl. intended) $bCautionExcl% > 2.0%")
        }
        if (tier2Langs.contains(l)) {
            if (c.scam > 0 && dPrec < 0.95) failures.add("Danger Precision $dPrec < 0.95")
            if (c.scam > 0 && rec < 0.80) failures.add("Caution+ Recall $rec < 0.80")
            if (c.benign > 0 && bDanger > 0.5) failures.add("Benign->Danger $bDanger% > 0.5%")
            if (c.benign > 0 && bCautionExcl > 3.0) failures.add("Benign->Caution (excl. intended) $bCautionExcl% > 3.0%")
        }
        val langPassed = failures.isEmpty()
        if (!langPassed && tier1Langs.contains(l)) {
            anyTier1LangFailed = true
        }
        if (!langPassed && tier2Langs.contains(l)) {
            anyTier2LangFailed = true
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
                intendedCautionCount = c.intendedCaution,
                benignToCautionExclIntendedPct = bCautionExcl,
                passedGates = langPassed,
                failureReasons = failures
            )
        )
    }

    val passedTier1 = if (hasTier1) (overallPassed && !anyTier1LangFailed) else true
    val passedTier2 = if (isTier2Only) (overallPassed && !anyTier2LangFailed) else !anyTier2LangFailed
    val allPassed = if (isTier2Only) passedTier2 else (passedTier1 && passedTier2)

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
        intendedCautionCount = overall.intendedCaution,
        intendedCautionTemplates = intendedCautionGroupIds,
        benignRaisedToCautionExclIntendedPercent = Math.round(bToCautionExclIntended * 100.0) / 100.0,
        passedTier1Gates = passedTier1,
        perLanguage = langList,
        adversarialMetrics = advMetrics,
        adversarialRecall = advMetrics.scamRecall,
        rulesOnlyRecall = Math.round(rulesRec * 1000.0) / 1000.0,
        rulesPlusMlRecall = Math.round(recall * 1000.0) / 1000.0
    )

    val reportHeader = when {
        isTier3Only -> "TIER 3 EVALUATION (UNGATED)"
        isTier2Only -> "TIER 2 GATES"
        else -> "TIER 1 GATES"
    }
    val gateStatusDisplay = when {
        isTier3Only -> "[REPORTED (ungated)]"
        allPassed -> "[PASS]"
        else -> "[FAIL: " + (if (!overallPassed) "Overall metrics miss; " else "") + (if (anyTier1LangFailed || anyTier2LangFailed) langList.filter { !it.passedGates && !tier3Langs.contains(it.lang) }.joinToString("; ") { "${it.lang} ${it.failureReasons.joinToString(", ")}" } else "") + "]"
    }

    println("""
        ==================== EVALUATION REPORT ($reportHeader) ====================
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
          Danger Precision:   ${evalMetrics.dangerPrecision} (Target: >= ${if (isTier2Only) "0.95" else if (isTier3Only) "N/A" else "0.97"})
          Caution+ Recall:    ${evalMetrics.cautionOrAboveRecall} (Target: >= ${if (isTier2Only) "0.80" else if (isTier3Only) "N/A" else "0.90"})
          Benign -> Danger:   ${evalMetrics.benignRaisedToDangerPercent}% (Target: <= ${if (isTier2Only) "0.5%" else if (isTier3Only) "N/A" else "0.3%"})
          Benign -> Caution+: ${evalMetrics.benignRaisedToCautionOrAbovePercent}% (Total) | ${evalMetrics.benignRaisedToCautionExclIntendedPercent}% (Excl. intended, Target: <= ${if (isTier2Only) "3.0%" else if (isTier3Only) "N/A" else "2.0%"})
          Gate Status:        $gateStatusDisplay
        -------------------------------------------------------------------------
        INTENDED CAUTION (BY PRODUCT DESIGN §Item 3):
          Unknown number + shortened link + delivery/promo text is CAUTION as designed.
          Intended Caution Rows: ${overall.intendedCaution} / ${overall.benign}
          Intended Caution Templates: $intendedCautionGroupIds
        -------------------------------------------------------------------------
        PER-LANGUAGE BREAKDOWN (§16.2 Gates):
    """.trimIndent())

    for (lm in langList) {
        val status = if (tier3Langs.contains(lm.lang)) {
            "[REPORTED (ungated)]"
        } else if (lm.passedGates) {
            "[PASS]"
        } else {
            "[FAIL: ${lm.failureReasons.joinToString(", ")}]"
        }
        println("  Language [${lm.lang.padEnd(7)}]: Total=${lm.total}, Scam=${lm.scam}, Benign=${lm.benign} | Prec=${lm.dangerPrecision}, Rec=${lm.recall}, B->Danger=${lm.benignToDangerPct}%, B->Caution=${lm.benignToCautionPct}% (Excl. Intended: ${lm.benignToCautionExclIntendedPct}%, Intended: ${lm.intendedCautionCount}) $status")
    }

    if (fnGroupIds.isNotEmpty()) {
        println("  False Negatives by template (group_id): $fnGroupIds")
    }
    if (fpGroupIds.isNotEmpty()) {
        println("  False Positives by template (group_id): $fpGroupIds")
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

    val finalGateLine = when {
        isTier3Only -> "TIER 3 GATES:         REPORTED (ungated)"
        isTier2Only -> "TIER 2 GATES PASSED:  ${if (passedTier2) "YES [PASS]" else "NO [FAIL]"}"
        else -> "TIER 1 GATES PASSED:  ${if (passedTier1) "YES [PASS]" else "NO [FAIL]"}"
    }

    println("""
        -------------------------------------------------------------------------
        RECALL COMPARISON:
          Rules-only Recall:  ${evalMetrics.rulesOnlyRecall} (Danger Prec: ${Math.round(rulesPrec * 1000.0) / 1000.0})
          Rules + ML Recall:  ${evalMetrics.rulesPlusMlRecall} (Danger Prec: ${evalMetrics.dangerPrecision})
        -------------------------------------------------------------------------
        $finalGateLine
        =========================================================================
    """.trimIndent())

    // Evaluate real-world corpus if present (§16.2 / user item 4)
    val realWorldFile = File("eval/real_world.jsonl").let { if (it.exists()) it else File("../eval/real_world.jsonl") }
    if (realWorldFile.exists()) {
        println("==================== REAL-WORLD EVALUATION (§16.2) ====================")
        val rwLines = realWorldFile.readLines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
        var currentGroupId: String? = null
        val contextMsgs = ArrayList<IncomingMessage>()
        var rwScoredCount = 0
        var rwCorrectCount = 0

        for (rwLine in rwLines) {
            val rwRow = try { json.decodeFromString<CorpusRow>(rwLine) } catch (_: Exception) { continue }
            if (currentGroupId != rwRow.group_id) {
                currentGroupId = rwRow.group_id
                contextMsgs.clear()
            }
            val rwApp = when (rwRow.app.uppercase()) {
                "SMS", "SMS_GOOGLE_MESSAGES" -> SourceApp.SMS_GOOGLE_MESSAGES
                "SMS_SAMSUNG_MESSAGES" -> SourceApp.SMS_SAMSUNG_MESSAGES
                "SMS_GENERIC" -> SourceApp.SMS_GENERIC
                "WHATSAPP_BUSINESS" -> SourceApp.WHATSAPP_BUSINESS
                else -> SourceApp.WHATSAPP
            }
            val msg = if (rwApp.isSms) {
                val sDisp = rwRow.sender_display ?: if (rwRow.sender_kind.uppercase() == "DLT_HEADER") "VM-SBIBNK-T" else "+919876543210"
                val p = DltHeaderParser.parse(sDisp)
                val sKind = when (rwRow.sender_kind.uppercase()) {
                    "DLT_HEADER" -> SenderKind.DLT_HEADER
                    "PERSONAL_NUMBER" -> SenderKind.PERSONAL_NUMBER
                    "SHORT_CODE" -> SenderKind.SHORT_CODE
                    "SAVED_CONTACT", "NAMED" -> SenderKind.SAVED_CONTACT
                    else -> p.senderKind
                }
                IncomingMessage(
                    fingerprint = rwRow.id,
                    source = SourceKind.NOTIFICATION,
                    app = rwApp,
                    conversationKey = "rw_${rwRow.group_id}",
                    senderDisplay = sDisp,
                    senderKind = sKind,
                    senderCountryCode = p.countryCode ?: "+91",
                    isGroup = rwRow.is_group,
                    text = rwRow.text,
                    attachmentHint = if (rwRow.text.endsWith(".apk")) "application/vnd.android.package-archive" else null,
                    receivedAtMillis = System.currentTimeMillis(),
                    dltHeaderPrefix = p.dltPrefix,
                    dltHeaderBrand = p.dltBrand,
                    dltHeaderSuffix = p.dltSuffix
                )
            } else {
                val sKind = if (rwRow.sender_kind.uppercase() == "NAMED") SenderKind.NAMED else SenderKind.NUMBER_ONLY
                IncomingMessage(
                    fingerprint = rwRow.id,
                    source = SourceKind.NOTIFICATION,
                    app = SourceApp.WHATSAPP,
                    conversationKey = "rw_${rwRow.group_id}",
                    senderDisplay = rwRow.sender_display ?: if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
                    senderKind = sKind,
                    senderCountryCode = "+91",
                    isGroup = rwRow.is_group,
                    text = rwRow.text,
                    attachmentHint = if (rwRow.text.endsWith(".apk")) "application/vnd.android.package-archive" else null,
                    receivedAtMillis = System.currentTimeMillis()
                )
            }

            if (rwRow.label.lowercase() == "context_only") {
                contextMsgs.add(msg)
                println("  [Context] ID=${rwRow.id}: Text=\"${rwRow.text}\" (Preceding context stored, not scored)")
            } else {
                val rwVerdict = engine.analyze(msg, context = contextMsgs)
                rwScoredCount++
                val expectedLevel = if (rwRow.label.lowercase() == "scam") AlertLevel.DANGER else AlertLevel.NONE
                val passed = rwVerdict.level == expectedLevel || (rwRow.label.lowercase() == "scam" && rwVerdict.level == AlertLevel.CAUTION)
                if (passed) rwCorrectCount++
                val sigList = rwVerdict.reasons.map { it.signalId }.joinToString(", ")
                println("  [Scored]  ID=${rwRow.id}: Text=\"${rwRow.text}\" -> Level=${rwVerdict.level}, Score=${rwVerdict.score}, RuleScore=${rwVerdict.ruleScore}, Signals=[$sigList] [${if (passed) "PASS" else "FAIL"}]")
                contextMsgs.add(msg)
            }
        }
        println("  Real-World Total Scored: $rwScoredCount, Correct: $rwCorrectCount / $rwScoredCount")
        println("=========================================================================\n")
    }

    if (outFile != null) {
        outFile.parentFile?.mkdirs()
        outFile.writeText(json.encodeToString(evalMetrics))
        println("Report written to: ${outFile.absolutePath}")
    }

    if (!allPassed) {
        exitProcess(2)
    }
}
