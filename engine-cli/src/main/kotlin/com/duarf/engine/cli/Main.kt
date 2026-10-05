package com.duarf.engine.cli

import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.*
import com.duarf.engine.pack.FilePackSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
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
    val passedGates: Boolean
)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        exitProcess(1)
    }

    when (args[0]) {
        "explain" -> runExplain(args.drop(1))
        "eval" -> runEval(args.drop(1))
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
    // Search common paths
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
        fingerprint = "cli-query",
        source = SourceKind.PASTE,
        app = SourceApp.UNKNOWN,
        conversationKey = null,
        senderDisplay = null,
        senderKind = SenderKind.NUMBER_ONLY,
        senderCountryCode = "+91",
        isGroup = false,
        text = text,
        attachmentHint = null,
        receivedAtMillis = System.currentTimeMillis()
    )

    val verdict = engine.analyze(msg)

    println("================ VERDICT ================")
    println("Level:        ${verdict.level}")
    println("Score:        ${verdict.score} (Rule: ${verdict.ruleScore}, Model: ${verdict.modelProbability ?: "N/A"})")
    println("Category:     ${verdict.category}")
    println("Engine Ver:   ${verdict.engineVersion}")
    println("---------------- REASONS ----------------")
    if (verdict.reasons.isEmpty()) {
        println("None")
    } else {
        verdict.reasons.forEachIndexed { i, r ->
            println("${i + 1}. [${r.signalId}] ${r.titleKey} - ${r.detailKey} args=${r.args} evidence=${r.evidence}")
        }
    }
    println("--------------- HIGHLIGHTS --------------")
    verdict.highlights.forEach {
        val spanText = if (it.start in text.indices && it.end <= text.length) text.substring(it.start, it.end) else "<out-of-bounds>"
        println("[${it.start}..${it.end}]: \"$spanText\"")
    }
    println("=========================================")
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
    val engine = DefaultScamEngine.fromPackSource(FilePackSource(packsDir))

    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    var totalRows = 0
    var totalScam = 0
    var totalBenign = 0

    var tpDanger = 0
    var tpCaution = 0
    var fnNone = 0

    var tnNone = 0
    var fpCaution = 0
    var fpDanger = 0

    val lines = inputFile.readLines()
    for (line in lines) {
        if (line.isBlank() || line.trim().startsWith("#")) continue
        val row = try {
            json.decodeFromString<CorpusRow>(line)
        } catch (_: Exception) {
            continue
        }

        totalRows++
        val isScam = row.label.lowercase() == "scam"
        if (isScam) totalScam++ else totalBenign++

        val sKind = when (row.sender_kind.uppercase()) {
            "NAMED" -> SenderKind.NAMED
            "UNKNOWN" -> SenderKind.UNKNOWN
            else -> SenderKind.NUMBER_ONLY
        }

        val msg = IncomingMessage(
            fingerprint = "eval-${row.id.ifEmpty { totalRows.toString() }}",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-$totalRows",
            senderDisplay = if (sKind == SenderKind.NAMED) "Contact" else "+919876543210",
            senderKind = sKind,
            senderCountryCode = "+91",
            isGroup = row.is_group,
            text = row.text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )

        val verdict = engine.analyze(msg)

        if (isScam) {
            when (verdict.level) {
                AlertLevel.DANGER -> tpDanger++
                AlertLevel.CAUTION -> tpCaution++
                AlertLevel.NONE -> fnNone++
            }
        } else {
            when (verdict.level) {
                AlertLevel.NONE -> tnNone++
                AlertLevel.CAUTION -> fpCaution++
                AlertLevel.DANGER -> fpDanger++
            }
        }
    }

    val dangerPrecision = if (tpDanger + fpDanger > 0) tpDanger.toDouble() / (tpDanger + fpDanger) else 1.0
    val cautionOrAboveRecall = if (totalScam > 0) (tpDanger + tpCaution).toDouble() / totalScam else 0.0
    val benignRaisedToDangerPercent = if (totalBenign > 0) (fpDanger.toDouble() / totalBenign) * 100.0 else 0.0
    val benignRaisedToCautionOrAbovePercent = if (totalBenign > 0) ((fpCaution + fpDanger).toDouble() / totalBenign) * 100.0 else 0.0

    // Milestone M1 gate: Danger precision >= 0.95, Caution-or-above recall >= 0.75
    val passedGates = dangerPrecision >= 0.95 && cautionOrAboveRecall >= 0.75

    val metrics = EvaluationMetrics(
        totalRows = totalRows,
        totalScam = totalScam,
        totalBenign = totalBenign,
        truePositivesDanger = tpDanger,
        truePositivesCaution = tpCaution,
        falseNegativesNone = fnNone,
        trueNegativesNone = tnNone,
        falsePositivesCaution = fpCaution,
        falsePositivesDanger = fpDanger,
        dangerPrecision = (dangerPrecision * 1000).toLong() / 1000.0,
        cautionOrAboveRecall = (cautionOrAboveRecall * 1000).toLong() / 1000.0,
        benignRaisedToDangerPercent = (benignRaisedToDangerPercent * 100).toLong() / 100.0,
        benignRaisedToCautionOrAbovePercent = (benignRaisedToCautionOrAbovePercent * 100).toLong() / 100.0,
        passedGates = passedGates
    )

    println("""
        ==================== EVALUATION REPORT ====================
        Total Rows:           $totalRows (Scam: $totalScam, Benign: $totalBenign)
        -----------------------------------------------------------
        Scam - DANGER (TP):   $tpDanger
        Scam - CAUTION (TP):  $tpCaution
        Scam - NONE (FN):     $fnNone
        -----------------------------------------------------------
        Benign - NONE (TN):   $tnNone
        Benign - CAUTION (FP):$fpCaution
        Benign - DANGER (FP): $fpDanger
        -----------------------------------------------------------
        Danger Precision:     ${metrics.dangerPrecision} (Target: >= 0.95)
        Caution+ Recall:      ${metrics.cautionOrAboveRecall} (Target: >= 0.75)
        Benign -> Danger:     ${metrics.benignRaisedToDangerPercent}% (Target: <= 0.3%)
        Benign -> Caution+:   ${metrics.benignRaisedToCautionOrAbovePercent}% (Target: <= 2.0%)
        -----------------------------------------------------------
        M1 GATES PASSED:      ${if (passedGates) "YES [PASS]" else "NO [FAIL]"}
        ===========================================================
    """.trimIndent())

    if (outFile != null) {
        outFile.parentFile?.mkdirs()
        outFile.writeText(json.encodeToString(metrics))
        println("Report written to: ${outFile.absolutePath}")
    }

    if (!passedGates) {
        exitProcess(2)
    }
}
