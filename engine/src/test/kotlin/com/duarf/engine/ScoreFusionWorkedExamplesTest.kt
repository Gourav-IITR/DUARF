// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine

import com.duarf.engine.model.AlertLevel
import com.duarf.engine.model.ScamCategory
import com.duarf.engine.model.Sensitivity
import com.duarf.engine.signal.FiredDampener
import com.duarf.engine.signal.FiredSignal
import com.duarf.engine.signal.ComboEngine
import com.duarf.engine.signal.ScoreFusion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreFusionWorkedExamplesTest {

    @Test
    fun `worked example 1 - one weak signal P01 alone stays NONE`() {
        val p01 = FiredSignal("P01", "urgency_deadline", 0.20, ScamCategory.PHISHING_BANK_KYC, null)
        val combos = ComboEngine.evaluateCombos(listOf(p01))

        val result = ScoreFusion.fuse(
            signals = listOf(p01),
            dampeners = emptyList(),
            combos = combos,
            modelProbability = null,
            sensitivity = Sensitivity.BALANCED
        )

        assertThat(result.score).isEqualTo(0.20)
        assertThat(result.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `worked example 2 - L01 alone from named sender is CAUTION`() {
        val l01 = FiredSignal("L01", "apk_file_or_link", 0.55, ScamCategory.MALICIOUS_APK, null)
        val combos = ComboEngine.evaluateCombos(listOf(l01))

        val result = ScoreFusion.fuse(
            signals = listOf(l01),
            dampeners = emptyList(),
            combos = combos,
            modelProbability = null,
            sensitivity = Sensitivity.BALANCED
        )

        assertThat(result.score).isEqualTo(0.55)
        assertThat(result.level).isEqualTo(AlertLevel.CAUTION)
    }

    @Test
    fun `worked example 3 - L01 from number-only sender is DANGER via Combo C02 floor`() {
        val l01 = FiredSignal("L01", "apk_file_or_link", 0.55, ScamCategory.MALICIOUS_APK, null)
        val s01 = FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)

        val signals = listOf(l01, s01)
        val combos = ComboEngine.evaluateCombos(signals)

        // Confirm Combo C02 fired
        assertThat(combos.any { it.id == "C02" }).isTrue()
        val c02 = combos.first { it.id == "C02" }
        assertThat(c02.floor).isEqualTo(0.85)

        val result = ScoreFusion.fuse(
            signals = signals,
            dampeners = emptyList(),
            combos = combos,
            modelProbability = null,
            sensitivity = Sensitivity.BALANCED
        )

        // Without C02, noisy-OR = 1 - (1-0.55)*(1-0.10) = 0.595 (which is below 0.72 Danger threshold)
        // With C02 floor 0.85, the rule score is 0.85 >= 0.72 -> DANGER
        assertThat(result.ruleScore).isEqualTo(0.85)
        assertThat(result.score).isEqualTo(0.85)
        assertThat(result.level).isEqualTo(AlertLevel.DANGER)
        assertThat(result.category).isEqualTo(ScamCategory.MALICIOUS_APK)
    }

    @Test
    fun `worked example 4 - genuine OTP delivery is NONE`() {
        // Genuine OTP delivery message does NOT fire A01; B01 fires
        val b01 = FiredDampener("B01", "otp_delivery_only", 0.40)

        val result = ScoreFusion.fuse(
            signals = emptyList(),
            dampeners = listOf(b01),
            combos = emptyList(),
            modelProbability = null,
            sensitivity = Sensitivity.BALANCED
        )

        assertThat(result.score).isEqualTo(0.0)
        assertThat(result.level).isEqualTo(AlertLevel.NONE)
    }

    @Test
    fun `worked example 5 - model at 0_99 with no concrete signal is CAUTION per Invariant 6`() {
        // High model probability 0.99, but no concrete L*, A*, P*, T* signals with weight >= 0.20
        val s01 = FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)

        val result = ScoreFusion.fuse(
            signals = listOf(s01),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.99,
            sensitivity = Sensitivity.BALANCED
        )

        // At Balanced sensitivity, Danger threshold is 0.72
        // Model alone or without concrete signals must be clamped to dangerThreshold - 0.001 (0.719)
        assertThat(result.score).isLessThan(0.72)
        assertThat(result.score).isAtLeast(0.45)
        assertThat(result.level).isEqualTo(AlertLevel.CAUTION)
    }

    @Test
    fun `worked example 6 - L05 shortener plus S01 with model 0_99 reaches CAUTION at most`() {
        val l05 = FiredSignal("L05", "url_shortener", 0.20, ScamCategory.PHISHING_BANK_KYC, null)
        val s01 = FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)

        val result = ScoreFusion.fuse(
            signals = listOf(l05, s01),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.99,
            sensitivity = Sensitivity.BALANCED
        )

        // Soft signal L05 + S01 + model cannot reach DANGER (capped at 0.719)
        assertThat(result.score).isLessThan(0.72)
        assertThat(result.score).isAtLeast(0.45)
        assertThat(result.level).isEqualTo(AlertLevel.CAUTION)
    }

    @Test
    fun `worked example 7 - soft pressure signals P01 and P02 with model 0_99 reach CAUTION at most`() {
        val p01 = FiredSignal("P01", "urgency_deadline", 0.20, ScamCategory.PHISHING_BANK_KYC, null)
        val p02 = FiredSignal("P02", "threat_account_block", 0.35, ScamCategory.PHISHING_BANK_KYC, null)
        val s01 = FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)

        val result = ScoreFusion.fuse(
            signals = listOf(p01, p02, s01),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.99,
            sensitivity = Sensitivity.BALANCED
        )

        assertThat(result.score).isLessThan(0.72)
        assertThat(result.score).isAtLeast(0.45)
        assertThat(result.level).isEqualTo(AlertLevel.CAUTION)
    }

    @Test
    fun `worked example 8 - lookalike domain L03 with model reaches DANGER`() {
        val l03 = FiredSignal("L03", "lookalike_domain", 0.60, ScamCategory.PHISHING_BANK_KYC, null)
        val s01 = FiredSignal("S01", "sender_number_only", 0.10, ScamCategory.OTHER_SUSPICIOUS, null)

        val result = ScoreFusion.fuse(
            signals = listOf(l03, s01),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.90,
            sensitivity = Sensitivity.BALANCED
        )

        // L03 is in DANGER_QUALIFYING_SIGNALS, so fused score can reach DANGER
        assertThat(result.score).isAtLeast(0.72)
        assertThat(result.level).isEqualTo(AlertLevel.DANGER)
    }

    @Test
    fun `worked example 9 - OTP ask A01 with model reaches DANGER`() {
        val a01 = FiredSignal("A01", "asks_otp_pin_cvv", 0.60, ScamCategory.OTP_ACCOUNT_TAKEOVER, null)

        val result = ScoreFusion.fuse(
            signals = listOf(a01),
            dampeners = emptyList(),
            combos = emptyList(),
            modelProbability = 0.85,
            sensitivity = Sensitivity.BALANCED
        )

        assertThat(result.score).isAtLeast(0.72)
        assertThat(result.level).isEqualTo(AlertLevel.DANGER)
    }
}
