package com.duarf.engine.signal

import com.duarf.engine.model.ScamCategory

data class FiredCombo(
    val id: String,
    val floor: Double,
    val category: ScamCategory,
    val memberSignalIds: Set<String>
)

object ComboEngine {

    fun evaluateCombos(signals: List<FiredSignal>): List<FiredCombo> {
        val firedSignalIds = signals.map { it.signalId }.toSet()
        val firedCombos = ArrayList<FiredCombo>()

        fun has(vararg ids: String): Boolean = ids.all { firedSignalIds.contains(it) }
        fun hasAny(vararg ids: String): Boolean = ids.any { firedSignalIds.contains(it) }

        // C01: L01 and any of P10, P12, P13, P02, P09 -> Floor 0.92, Category MALICIOUS_APK
        if (has("L01") && hasAny("P10", "P12", "P13", "P02", "P09")) {
            val matched = setOf("L01") + setOf("P10", "P12", "P13", "P02", "P09").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C01", 0.92, ScamCategory.MALICIOUS_APK, matched))
        }

        // C02: L01 and S01 -> Floor 0.85, Category MALICIOUS_APK
        if (has("L01", "S01")) {
            firedCombos.add(FiredCombo("C02", 0.85, ScamCategory.MALICIOUS_APK, setOf("L01", "S01")))
        }

        // C03: A01 and any of P10, P02, S01 -> Floor 0.85, Category OTP_ACCOUNT_TAKEOVER
        if (has("A01") && hasAny("P10", "P02", "S01")) {
            val matched = setOf("A01") + setOf("P10", "P02", "S01").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C03", 0.85, ScamCategory.OTP_ACCOUNT_TAKEOVER, matched))
        }

        // C04: P03 and any of A06, A03, P10 -> Floor 0.88, Category AUTHORITY_DIGITAL_ARREST
        if (has("P03") && hasAny("A06", "A03", "P10")) {
            val matched = setOf("P03") + setOf("A06", "A03", "P10").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C04", 0.88, ScamCategory.AUTHORITY_DIGITAL_ARREST, matched))
        }

        // C05: A04 -> Floor 0.80, Category UPI_PAYMENT_FRAUD
        if (has("A04")) {
            firedCombos.add(FiredCombo("C05", 0.80, ScamCategory.UPI_PAYMENT_FRAUD, setOf("A04")))
        }

        // C06: P04 and any of A02, A03, P01, or any L* signal -> Floor 0.85, Category UTILITY_DISCONNECT
        val hasAnyL = firedSignalIds.any { it.startsWith("L") }
        if (has("P04") && (hasAny("A02", "A03", "P01") || hasAnyL)) {
            val matched = setOf("P04") + firedSignalIds.filter { it in setOf("A02", "A03", "P01") || it.startsWith("L") }
            firedCombos.add(FiredCombo("C06", 0.85, ScamCategory.UTILITY_DISCONNECT, matched))
        }

        // C07: (L02 or L03 or L09) and any of P02, A07, A05 -> Floor 0.88, Category PHISHING_BANK_KYC
        if (hasAny("L02", "L03", "L09") && hasAny("P02", "A07", "A05")) {
            val matched = firedSignalIds.filter { it in setOf("L02", "L03", "L09", "P02", "A07", "A05") }.toSet()
            firedCombos.add(FiredCombo("C07", 0.88, ScamCategory.PHISHING_BANK_KYC, matched))
        }

        // C08: P06 and any of A08, A03, L12 -> Floor 0.75, Category JOB_TASK
        if (has("P06") && hasAny("A08", "A03", "L12")) {
            val matched = setOf("P06") + setOf("A08", "A03", "L12").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C08", 0.75, ScamCategory.JOB_TASK, matched))
        }

        // C09: P07 and any of A08, L12, A03 -> Floor 0.75, Category INVESTMENT_TRADING
        if (has("P07") && hasAny("A08", "L12", "A03")) {
            val matched = setOf("P07") + setOf("A08", "L12", "A03").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C09", 0.75, ScamCategory.INVESTMENT_TRADING, matched))
        }

        // C10: A02 (remote-access app) and any of P10, P02, P09 -> Floor 0.90, Category REMOTE_ACCESS
        val hasRemoteAccess = signals.any { it.signalId == "A02" && it.category == ScamCategory.REMOTE_ACCESS }
        if (hasRemoteAccess && hasAny("P10", "P02", "P09")) {
            val matched = setOf("A02") + setOf("P10", "P02", "P09").filter { firedSignalIds.contains(it) }
            firedCombos.add(FiredCombo("C10", 0.90, ScamCategory.REMOTE_ACCESS, matched))
        }

        // C11: S04 and (any A* signal or any L* signal) -> Floor 0.82, Category from S04
        val hasAnyA = firedSignalIds.any { it.startsWith("A") }
        if (has("S04") && (hasAnyA || hasAnyL)) {
            val matched = setOf("S04") + firedSignalIds.filter { it.startsWith("A") || it.startsWith("L") }
            val s04Category = signals.firstOrNull { it.signalId == "S04" }?.category ?: ScamCategory.PHISHING_BANK_KYC
            firedCombos.add(FiredCombo("C11", 0.82, s04Category, matched))
        }

        // C12: S04 and any of P01, P02, P03, P04 -> Floor 0.82, Category from specific threat or S04
        if (has("S04") && hasAny("P01", "P02", "P03", "P04")) {
            val matched = setOf("S04") + setOf("P01", "P02", "P03", "P04").filter { firedSignalIds.contains(it) }
            val category = when {
                firedSignalIds.contains("P04") -> ScamCategory.UTILITY_DISCONNECT
                firedSignalIds.contains("P03") -> ScamCategory.AUTHORITY_DIGITAL_ARREST
                firedSignalIds.contains("P02") -> ScamCategory.PHISHING_BANK_KYC
                else -> signals.firstOrNull { it.signalId == "S04" }?.category ?: ScamCategory.PHISHING_BANK_KYC
            }
            firedCombos.add(FiredCombo("C12", 0.82, category, matched))
        }

        return firedCombos
    }
}
