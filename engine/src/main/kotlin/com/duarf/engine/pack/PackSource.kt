package com.duarf.engine.pack

import java.io.File

interface PackSource {
    fun readText(path: String): String
    fun readLines(path: String): List<String>
    fun listFiles(dir: String): List<String>
    fun readBytes(path: String): ByteArray?
}

class FilePackSource(private val rootDir: File) : PackSource {
    override fun readText(path: String): String =
        File(rootDir, path).readText()

    override fun readLines(path: String): List<String> {
        val file = File(rootDir, path)
        return if (file.exists()) file.readLines() else emptyList()
    }

    override fun listFiles(dir: String): List<String> {
        val targetDir = File(rootDir, dir)
        return targetDir.listFiles()?.map { it.name } ?: emptyList()
    }

    override fun readBytes(path: String): ByteArray? {
        val file = File(rootDir, path)
        return if (file.exists()) file.readBytes() else null
    }
}
