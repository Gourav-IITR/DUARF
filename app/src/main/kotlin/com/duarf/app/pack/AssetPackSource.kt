package com.duarf.app.pack

import android.content.Context
import com.duarf.engine.pack.PackSource
import java.io.BufferedReader
import java.io.InputStreamReader

class AssetPackSource(
    private val context: Context,
    private val basePath: String = "packs"
) : PackSource {

    override fun readText(path: String): String {
        val assetPath = if (basePath.isEmpty()) path else "$basePath/$path"
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun readLines(path: String): List<String> {
        val assetPath = if (basePath.isEmpty()) path else "$basePath/$path"
        return try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLines()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun listFiles(dir: String): List<String> {
        val assetDir = if (basePath.isEmpty()) dir else "$basePath/$dir"
        return try {
            context.assets.list(assetDir)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun readBytes(path: String): ByteArray? {
        val assetPath = if (basePath.isEmpty()) path else "$basePath/$path"
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
