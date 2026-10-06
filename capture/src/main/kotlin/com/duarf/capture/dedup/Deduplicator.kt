package com.duarf.capture.dedup

import com.duarf.engine.model.IncomingMessage
import java.security.MessageDigest
import java.util.LinkedHashMap

class Deduplicator(
    private val maxLruSize: Int = 500,
    private val contextTtlMillis: Long = 10 * 60 * 1000L, // 10 minutes
    private val maxContextMessages: Int = 3,
    private val contextWindowMillis: Long = 5 * 60 * 1000L // 5 minutes
) {

    // In-memory LRU cache of recently seen fingerprints (§5.4)
    private val lruCache = object : LinkedHashMap<String, Long>(maxLruSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxLruSize
        }
    }

    // SMS dedup cache: (sender + text) -> timestamp for 5-minute sliding window
    private val smsDedupCache = object : LinkedHashMap<String, Long>(maxLruSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxLruSize
        }
    }

    // Short-term RAM-only context buffer per conversationKey (§5.4)
    private val contextBuffer = HashMap<String, MutableList<IncomingMessage>>()

    @Synchronized
    fun isDuplicate(fingerprint: String): Boolean {
        if (lruCache.containsKey(fingerprint)) {
            return true
        }
        lruCache[fingerprint] = System.currentTimeMillis()
        return false
    }

    @Synchronized
    fun isDuplicateMessage(message: IncomingMessage): Boolean {
        if (message.app.isSms) {
            val key = "${message.senderDisplay?.trim()?.lowercase() ?: ""}|${message.text.trim()}"
            val now = System.currentTimeMillis()
            val lastSeen = smsDedupCache[key]
            if (lastSeen != null && (now - lastSeen) <= contextWindowMillis) {
                return true
            }
            smsDedupCache[key] = now
            return false
        }
        return isDuplicate(message.fingerprint)
    }

    @Synchronized
    fun getContextAndRecord(message: IncomingMessage): List<IncomingMessage> {
        val key = message.conversationKey ?: return emptyList()
        val now = System.currentTimeMillis()

        // Clean up expired entries in buffer
        val list = contextBuffer.getOrPut(key) { ArrayList() }
        list.removeAll { (now - it.receivedAtMillis) > contextTtlMillis }

        // Context messages from the same sender within 5 minutes
        val sender = message.senderDisplay ?: ""
        val contextForEngine = list.filter {
            (it.senderDisplay ?: "") == sender && (now - it.receivedAtMillis) <= contextWindowMillis
        }.takeLast(maxContextMessages)

        // Append current message to buffer
        list.add(message)
        if (list.size > maxContextMessages * 2) {
            list.removeAt(0)
        }

        return contextForEngine
    }

    @Synchronized
    fun clear() {
        lruCache.clear()
        smsDedupCache.clear()
        contextBuffer.clear()
    }

    companion object {
        fun computeFingerprint(
            conversationKey: String?,
            senderDisplay: String?,
            text: String,
            timestamp: Long
        ): String {
            val raw = "${conversationKey ?: ""}|${senderDisplay ?: ""}|$text|$timestamp"
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
