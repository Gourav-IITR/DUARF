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

    // Hard signals (§10): L01, L10, L11, A01, A02, A04
    private val HARD_SIGNALS = setOf("L01", "L10", "L11", "A01", "A02", "A04")

    // High-risk link signals qualifying for Danger: L02, L03, L07, L09
    private val HIGH_RISK_LINK_SIGNALS = setOf("L02", "L03", "L07", "L09")

    // Full set of signal IDs that permit DANGER (spec §10 product rule):
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

        // General product rule (spec §10, Invariant 6):
        // DANGER requires a hard signal (L01, L10, L11, A01, A02, A04),
        // an active combo floor >= dangerThreshold, or L02/L03/L07/L09.
        // Soft signals (L05 shortener, L06 TLD, S*, P*, etc.) plus the model can reach CAUTION at most.
        val qualifiesForDanger = signals.any { it.signalId in DANGER_QUALIFYING_SIGNALS } ||
                (highestCombo != null && comboFloor >= dangerThreshold)

        if (!qualifiesForDanger && score >= dangerThreshold) {
            score = dangerThreshold - 0.001
        }

        val level = when {
            score >= dangerThreshold -> AlertLevel.DANGER
            score >= cautionThreshold -> AlertLevel.CAUTION
            else -> AlertLevel.NONE
        }

        return FusionResult(
            level = level,
            score = Math.round(score * 1000.0) / 1000.0, // round to 3 decimals
            ruleScore = Math.round(ruleScore * 1000.0) / 1000.0,
            modelProbability = modelProbability,
            category = category,
            topCombo = highestCombo
        )
    }
}
