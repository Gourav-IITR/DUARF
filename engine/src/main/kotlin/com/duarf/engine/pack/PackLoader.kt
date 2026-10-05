package com.duarf.engine.pack

import com.duarf.engine.extract.BrandDefinition
import kotlinx.serialization.json.Json

object PackLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(source: PackSource): LoadedPacks {
        // 1. Load rules.json
        val rules = try {
            json.decodeFromString<RulesPack>(source.readText("rules.json"))
        } catch (_: Exception) {
            RulesPack()
        }

        // 2. Load brands.json
        val brands = try {
            json.decodeFromString<List<BrandDefinition>>(source.readText("brands.json"))
        } catch (_: Exception) {
            emptyList()
        }

        // 3. Load language packs from lang/
        val langFiles = source.listFiles("lang").filter { it.endsWith(".json") }
        val languages = langFiles.mapNotNull { fileName ->
            try {
                val content = source.readText("lang/$fileName")
                json.decodeFromString<LanguagePack>(content)
            } catch (_: Exception) {
                null
            }
        }

        // 4. Load lists
        val shorteners = source.readLines("lists/shorteners.txt")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val riskyTlds = source.readLines("lists/risky_tlds.txt")
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val upiHandles = source.readLines("lists/upi_handles.txt")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val remoteApps = source.readLines("lists/remote_apps.txt")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val pslLines = source.readLines("lists/psl.dat")

        val blocklist = source.readLines("lists/blocklist.txt")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val modelBytes = try {
            source.readBytes("model/model.bin")
        } catch (_: Exception) {
            null
        }

        return LoadedPacks(
            rules = rules,
            brands = brands,
            languages = languages,
            shorteners = shorteners,
            riskyTlds = riskyTlds,
            upiHandles = upiHandles,
            remoteApps = remoteApps,
            pslLines = pslLines,
            blocklist = blocklist,
            modelBytes = modelBytes
        )
    }
}
