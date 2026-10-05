package com.duarf.engine.signal

import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.ScamCategory
import com.duarf.engine.model.Sensitivity

data class FusionResult(
    val level: AlertLevel,
    val score: Double,
    val ruleScore: Double,
    val modelProbability: Double?,
    val category: ScamCategory,
    val topCombo: FiredCombo?
)

object ScoreFusion {

    private val HARD_SIGNALS = setOf("L01", "L10", "L11", "A01", "A02", "A04")

    fun fuse(
        signals: List<FiredSignal>,
        dampeners: List<FiredDampener>,
        combos: List<FiredCombo>,
        modelProbability: Double?,
        sensitivity: Sensitivity = Sensitivity.BALANCED
    ): FusionResult {
        // Effective weights: halved for context signals (§5.4, §10)
        var productOneMinusW = 1.0
        for (s in signals) {
            val effectiveWeight = if (s.isFromContext) s.weight / 2.0 else s.weight
            productOneMinusW *= (1.0 - effectiveWeight)
        }
        val r0 = 1.0 - productOneMinusW

        // Dampeners: skipped if a hard signal fired
        val hasHardSignal = signals.any { it.signalId in HARD_SIGNALS }
        val r1 = if (hasHardSignal || dampeners.isEmpty()) {
            r0
        } else {
            var productOneMinusD = 1.0
            for (d in dampeners) {
                productOneMinusD *= (1.0 - d.factor)
            }
            r0 * productOneMinusD
        }

        // Combo floor
        val hasB04 = dampeners.any { it.signalId == "B04" }
        val highestCombo = combos.maxByOrNull { it.floor }
        val comboFloor = if (highestCombo != null) {
            if (hasB04 && !hasHardSignal) {
                0.0 // Combo floors without hard signals ignored when B04 fired (§10)
            } else {
                highestCombo.floor
            }
        } else {
            0.0
        }

        val ruleScore = maxOf(r1, comboFloor)

        // Model contribution
        val mPrime = if (modelProbability != null) {
            val clamped = ((modelProbability - 0.5) / 0.5).coerceIn(0.0, 1.0)
            0.8 * clamped
        } else {
            0.0
        }

        var score = 1.0 - (1.0 - ruleScore) * (1.0 - mPrime)

        // Category determination (§7.4):
        // If combo fired, it chooses the category; else highest weight positive signal; else OTHER_SUSPICIOUS
        val category = when {
            highestCombo != null -> highestCombo.category
            signals.isNotEmpty() -> signals.maxByOrNull { it.weight }?.category ?: ScamCategory.OTHER_SUSPICIOUS
            else -> ScamCategory.OTHER_SUSPICIOUS
        }

        // Sensitivity thresholds (§10)
        val (cautionThreshold, dangerThreshold) = when (sensitivity) {
            Sensitivity.LOW -> Pair(0.55, 0.80)
            Sensitivity.BALANCED -> Pair(0.45, 0.72)
            Sensitivity.HIGH -> Pair(0.35, 0.65)
        }

        // Invariant 6: Concrete signal = any fired L*, A*, P*, T* signal with weight >= 0.20
        // If none fired, clamp score to just below the Danger threshold
        val hasConcreteSignal = signals.any { s ->
            val isTargetFamily = s.signalId.startsWith("L") || s.signalId.startsWith("A") ||
                    s.signalId.startsWith("P") || s.signalId.startsWith("T")
            isTargetFamily && s.weight >= 0.20
        }

        if (!hasConcreteSignal && score >= dangerThreshold) {
            score = dangerThreshold - 0.001
        }

        val level = when {
            score >= dangerThreshold -> AlertLevel.DANGER
            score >= cautionThreshold -> AlertLevel.CAUTION
            else -> AlertLevel.NONE
        }

        return FusionResult(
            level = level,
            score = (score * 1000).toLong() / 1000.0, // round to 3 decimals
            ruleScore = (ruleScore * 1000).toLong() / 1000.0,
            modelProbability = modelProbability,
            category = category,
            topCombo = highestCombo
        )
    }
}
