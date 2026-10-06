package com.duarf.engine.signal

import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.ScamCategory
import com.duarf.engine.model.Sensitivity
import com.duarf.engine.pack.FilePackSource
import com.duarf.engine.pack.PackLoader
import com.duarf.engine.pack.RulesPack
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import java.util.Random

class DangerQualifyingGuardrailTest {

    private val expectedDangerSignals = setOf(
        "L01", "L02", "L03", "L07", "L09", "L10", "L11",
        "A01", "A02", "A04"
    )

    private fun findPacksDir(): File {
        return listOf(
            File("packs"),
            File("../packs"),
            File("../../packs")
        ).first { it.exists() && File(it, "rules.json").exists() }
    }

    private fun findArchitectureDoc(): File {
        return listOf(
            File("docs/ARCHITECTURE.md"),
            File("../docs/ARCHITECTURE.md"),
            File("../../docs/ARCHITECTURE.md")
        ).first { it.exists() }
    }

    @Test
    fun `DANGER_QUALIFYING_SIGNALS exactly equals documented set across code, rules json, and ARCHITECTURE doc`() {
        // 1. Code constant assertion
        assertThat(ScoreFusion.DANGER_QUALIFYING_SIGNALS)
            .containsExactlyElementsIn(expectedDangerSignals)

        // 2. rules.json via PackLoader
        val packsDir = findPacksDir()
        val packs = PackLoader.load(FilePackSource(packsDir))
        assertThat(packs.rules.dangerQualifyingSignals.toSet())
            .containsExactlyElementsIn(expectedDangerSignals)

        // 3. Raw rules.json decoding
        val rulesJsonText = File(packsDir, "rules.json").readText()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val decodedRules = json.decodeFromString<RulesPack>(rulesJsonText)
        assertThat(decodedRules.dangerQualifyingSignals.toSet())
            .containsExactlyElementsIn(expectedDangerSignals)

        // 4. ARCHITECTURE.md documentation assertion
        val archDoc = findArchitectureDoc()
        val archContent = archDoc.readText()
        val docPattern = Regex("""Danger qualifying signals[^\n:]*:\s*([A-Za-z0-9,\s]+)""")
        val matchDoc = docPattern.find(archContent)
        assertWithMessage("Danger qualifying signals must be explicitly documented in docs/ARCHITECTURE.md")
            .that(matchDoc).isNotNull()

        val documentedSignals = matchDoc!!.groupValues[1]
            .split(",")
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
            .toSet()

        assertThat(documentedSignals)
            .containsExactlyElementsIn(expectedDangerSignals)
        assertThat(documentedSignals)
            .containsExactlyElementsIn(ScoreFusion.DANGER_QUALIFYING_SIGNALS)
    }

    @Test
    fun `property test - signal sets without qualifying signal and without combo floor ge dangerThreshold never produce DANGER`() {
        val packsDir = findPacksDir()
        val packs = PackLoader.load(FilePackSource(packsDir))
        val allSignalIds = packs.rules.signals.map { it.id }.toSet()
        val nonQualifyingSignalIds = (allSignalIds - ScoreFusion.DANGER_QUALIFYING_SIGNALS).toList()

        val random = Random(42L)
        val targetTrials = 10_000
        var completedTrials = 0

        while (completedTrials < targetTrials) {
            val sensitivity = Sensitivity.entries[random.nextInt(Sensitivity.entries.size)]
            val dangerThreshold = when (sensitivity) {
                Sensitivity.LOW -> 0.80
                Sensitivity.BALANCED -> 0.72
                Sensitivity.HIGH -> 0.65
            }

            // Pick 0 to 15 random non-qualifying signals
            val numSignals = random.nextInt(16)
            val selectedIds = nonQualifyingSignalIds.shuffled(random).take(numSignals)
            val signals = selectedIds.map { id ->
                FiredSignal(
                    signalId = id,
                    name = "test_$id",
                    weight = (random.nextInt(90) + 10) / 100.0, // 0.10 to 0.99
                    category = ScamCategory.entries[random.nextInt(ScamCategory.entries.size)],
                    evidenceSpan = null,
                    isFromContext = random.nextBoolean()
                )
            }

            // Evaluate real combos
            val evaluatedCombos = ComboEngine.evaluateCombos(signals)
            // Filter combos to satisfy the property precondition: no combo floor >= dangerThreshold
            val qualifyingCombos = evaluatedCombos.filter { it.floor < dangerThreshold }

            // Random dampeners: 0 to 3 dampeners
            val numDampeners = random.nextInt(4)
            val dampenerPool = listOf("B01", "B02", "B03", "B04", "B05", "B06")
            val dampeners = dampenerPool.shuffled(random).take(numDampeners).map { id ->
                FiredDampener(
                    signalId = id,
                    name = "dampener_$id",
                    factor = (random.nextInt(60) + 10) / 100.0 // 0.10 to 0.69
                )
            }

            // Test adversarial model probability (1.0 for at least half the trials)
            val modelProbability = if (random.nextBoolean()) {
                1.0
            } else {
                random.nextDouble()
            }

            val isSms = random.nextBoolean()

            // Execute fusion
            val result = ScoreFusion.fuse(
                signals = signals,
                dampeners = dampeners,
                combos = qualifyingCombos,
                modelProbability = modelProbability,
                sensitivity = sensitivity,
                isSms = isSms
            )

            // Invariant 6 guarantee: never DANGER without a qualifying signal or qualifying combo floor
            assertWithMessage(
                "Trial $completedTrials failed Invariant 6! " +
                    "Signals: ${signals.map { "${it.signalId}:${it.weight}" }}, " +
                    "Combos: ${qualifyingCombos.map { "${it.id}:${it.floor}" }}, " +
                    "ModelProb: $modelProbability, Sensitivity: $sensitivity, isSms: $isSms, " +
                    "Score: ${result.score}, Level: ${result.level}"
            ).that(result.level).isNotEqualTo(AlertLevel.DANGER)

            assertWithMessage(
                "Trial $completedTrials score must be strictly less than dangerThreshold ($dangerThreshold)! " +
                    "Got score: ${result.score}"
            ).that(result.score).isLessThan(dangerThreshold)

            completedTrials++
        }

        assertThat(completedTrials).isEqualTo(targetTrials)
    }

    @Test
    fun `positive control - qualifying signal or combo floor ge dangerThreshold CAN produce DANGER`() {
        // 1. Qualifying signals with model prob or weight can reach DANGER
        for (qualifyingId in ScoreFusion.DANGER_QUALIFYING_SIGNALS) {
            val signal = FiredSignal(
                signalId = qualifyingId,
                name = "qualifying_$qualifyingId",
                weight = 0.60,
                category = ScamCategory.PHISHING_BANK_KYC,
                evidenceSpan = null
            )
            val result = ScoreFusion.fuse(
                signals = listOf(signal),
                dampeners = emptyList(),
                combos = emptyList(),
                modelProbability = 0.95,
                sensitivity = Sensitivity.BALANCED
            )
            assertThat(result.level).isEqualTo(AlertLevel.DANGER)
            assertThat(result.score).isAtLeast(0.72)
        }

        // 2. Combo floor >= dangerThreshold produces DANGER
        val c02Combo = FiredCombo(
            id = "C02",
            floor = 0.85,
            category = ScamCategory.MALICIOUS_APK,
            memberSignalIds = setOf("L01", "S01")
        )
        val comboResult = ScoreFusion.fuse(
            signals = emptyList(),
            dampeners = emptyList(),
            combos = listOf(c02Combo),
            modelProbability = null,
            sensitivity = Sensitivity.BALANCED
        )
        assertThat(comboResult.level).isEqualTo(AlertLevel.DANGER)
        assertThat(comboResult.score).isAtLeast(0.72)
    }
}
