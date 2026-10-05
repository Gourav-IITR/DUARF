package com.duarf.engine.pack

import com.duarf.engine.extract.BrandDefinition
import kotlinx.serialization.Serializable

@Serializable
data class LanguagePack(
    val code: String,
    val displayName: String,
    val script: String,
    val tier: Int,
    val reviewedBy: String? = null,
    val lexicons: Map<String, List<String>> = emptyMap()
)

@Serializable
data class RulesPack(
    val version: Int = 1,
    val signals: List<RuleSignalDef> = emptyList(),
    val combos: List<RuleComboDef> = emptyList()
)

@Serializable
data class RuleSignalDef(
    val id: String,
    val name: String,
    val weight: Double,
    val category: String,
    val reasonTitle: String? = null,
    val reasonDetail: String? = null
)

@Serializable
data class RuleComboDef(
    val id: String,
    val condition: String,
    val floor: Double,
    val category: String
)

data class LoadedPacks(
    val rules: RulesPack,
    val brands: List<BrandDefinition>,
    val languages: List<LanguagePack>,
    val shorteners: Set<String>,
    val riskyTlds: Set<String>,
    val upiHandles: Set<String>,
    val remoteApps: Set<String>,
    val pslLines: List<String>,
    val blocklist: Set<String>
)
