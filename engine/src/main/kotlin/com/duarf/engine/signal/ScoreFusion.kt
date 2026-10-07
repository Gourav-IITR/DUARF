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
    val topCombo: FiredCombo?,
    val mPrime: Double = 0.0
)

object ScoreFusion {

    // Hard signals (§10): L01, L10, L11, A01, A02, A04
    private val HARD_SIGNALS = setOf("L01", "L10", "L11", "A01", "A02", "A04")

    // High-risk link signals qualifying for Danger: L02, L03, L07, L09
    private val HIGH_RISK_LINK_SIGNALS = setOf("L02", "L03", "L07", "L09")

    // Full set of signal IDs that permit DANGER (spec §10 product rule, Invariant 6):
    // L01: apk_file_or_link
    // L02: brand_domain_mismatch
    // L03: lookalike_domain
    // L07: punycode_or_mixed_script_domain
    // L09: gov_claim_non_gov_domain
    // L10: url_userinfo_trick
    // L11: blocklisted_domain
    // A01: asks_otp_pin_cvv
    // A02: asks_install_app
    // A04: upi_pin_to_receive
    val DANGER_QUALIFYING_SIGNALS = HARD_SIGNALS + HIGH_RISK_LINK_SIGNALS

    fun fuse(
        signals: List<FiredSignal>,
        dampeners: List<FiredDampener>,
        combos: List<FiredCombo>,
        modelProbability: Double?,
        sensitivity: Sensitivity = Sensitivity.BALANCED,
        isSms: Boolean = false
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
        val hasB05 = dampeners.any { it.signalId == "B05" }
        val hasB06 = dampeners.any { it.signalId == "B06" }
        val highestCombo = combos.maxByOrNull { it.floor }
        val comboFloor = if (highestCombo != null) {
            if ((hasB04 || hasB05 || hasB06) && !hasHardSignal) {
                0.0 // Combo floors without hard signals ignored when B04, B05, or B06 fired (§10)
            } else {
                highestCombo.floor
            }
        } else {
            0.0
        }

        // Sensitivity thresholds (§10)
        val (cautionThreshold, dangerThreshold) = when (sensitivity) {
            Sensitivity.LOW -> Pair(0.55, 0.80)
            Sensitivity.BALANCED -> Pair(0.45, 0.72)
            Sensitivity.HIGH -> Pair(0.35, 0.65)
        }

        var ruleScore = maxOf(r1, comboFloor)

        // Spec §10: S05 alone, with no A*, L*, P02–P04 fired, is capped below the Caution threshold.
        // A sender mismatch with nothing asked, linked or threatened is not actionable.
        val hasActionOrLinkOrThreat = signals.any {
            it.signalId.startsWith("A") || it.signalId.startsWith("L") || it.signalId in setOf("P02", "P03", "P04")
        }
        val isS05Alone = signals.any { it.signalId == "S05" } && !hasActionOrLinkOrThreat
        if (isS05Alone) {
            val s05Cap = cautionThreshold - 0.001
            if (ruleScore > s05Cap) {
                ruleScore = s05Cap
            }
        }

        // Model contribution: capped to 0 when B05 or B06 is present
        val baseMPrime = if (modelProbability != null && !hasB05 && !hasB06) {
            val clamped = ((modelProbability - 0.5) / 0.5).coerceIn(0.0, 1.0)
            0.8 * clamped
        } else {
            0.0
        }

        // Cap model influence for SMS (Caution at most without a rule signal)
        val mPrime = if (isSms && ruleScore < 0.20) {
            minOf(baseMPrime, 0.55)
        } else {
            baseMPrime
        }

        var score = 1.0 - (1.0 - ruleScore) * (1.0 - mPrime)
        if (isS05Alone) {
            val s05Cap = cautionThreshold - 0.001
            if (score > s05Cap) {
                score = s05Cap
            }
        }

        // Category determination (§7.4):
        // If combo fired, it chooses the category; else highest weight positive signal; else OTHER_SUSPICIOUS
        val category = when {
            highestCombo != null -> highestCombo.category
            signals.isNotEmpty() -> signals.maxByOrNull { it.weight }?.category ?: ScamCategory.OTHER_SUSPICIOUS
            else -> ScamCategory.OTHER_SUSPICIOUS
        }

        // General product rule (spec §10, Invariant 6):
        // DANGER requires a hard signal (L01, L10, L11, A01, A02, A04),
        // an active combo floor >= dangerThreshold, or L02/L03/L07/L09.
        // Soft signals (L05 shortener, L06 TLD, S*, P*, etc.) plus the model can reach CAUTION at most.
        val qualifiesForDanger = signals.any { it.signalId in DANGER_QUALIFYING_SIGNALS } ||
                (highestCombo != null && comboFloor >= dangerThreshold)

        var roundedScore = Math.round(score * 1000.0) / 1000.0
        if (!qualifiesForDanger && roundedScore >= dangerThreshold) {
            roundedScore = Math.round((dangerThreshold - 0.001) * 1000.0) / 1000.0
        }

        val level = when {
            roundedScore >= dangerThreshold -> AlertLevel.DANGER
            roundedScore >= cautionThreshold -> AlertLevel.CAUTION
            else -> AlertLevel.NONE
        }

        return FusionResult(
            level = level,
            score = roundedScore,
            ruleScore = Math.round(ruleScore * 1000.0) / 1000.0,
            modelProbability = modelProbability,
            category = category,
            topCombo = highestCombo,
            mPrime = mPrime
        )
    }
}
