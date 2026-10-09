// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.signal

import java.util.ArrayDeque

data class LexiconPattern(
    val intent: String,
    val phrase: String
)

data class LexiconMatch(
    val intent: String,
    val phrase: String,
    val start: Int,
    val end: Int
)

class AhoCorasick(patterns: List<LexiconPattern>) {

    private class Node {
        val children = HashMap<Char, Node>()
        var fail: Node? = null
        val outputs = ArrayList<LexiconPattern>()
    }

    private val root = Node()

    init {
        // Build trie
        for (p in patterns) {
            val phrase = p.phrase.lowercase().trim()
            if (phrase.isEmpty()) continue

            var curr = root
            for (c in phrase) {
                curr = curr.children.getOrPut(c) { Node() }
            }
            curr.outputs.add(p)
        }

        // Build failure links using BFS
        val queue = ArrayDeque<Node>()
        for (child in root.children.values) {
            child.fail = root
            queue.add(child)
        }

        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            for ((char, child) in curr.children) {
                queue.add(child)

                var failNode = curr.fail
                while (failNode != null && !failNode.children.containsKey(char)) {
                    failNode = failNode.fail
                }
                val targetFail = failNode?.children?.get(char) ?: root
                child.fail = targetFail
                child.outputs.addAll(targetFail.outputs)
            }
        }
    }

    fun findMatches(text: String): List<LexiconMatch> {
        val lowerText = text.lowercase()
        val results = ArrayList<LexiconMatch>()
        var curr: Node? = root

        for (i in lowerText.indices) {
            val c = lowerText[i]

            while (curr != null && !curr.children.containsKey(c)) {
                curr = curr.fail
            }

            curr = curr?.children?.get(c) ?: root

            for (output in curr.outputs) {
                val matchLen = output.phrase.length
                val start = i - matchLen + 1
                val end = i + 1

                if (isWordBoundary(lowerText, start, end)) {
                    results.add(
                        LexiconMatch(
                            intent = output.intent,
                            phrase = output.phrase,
                            start = start,
                            end = end
                        )
                    )
                }
            }
        }

        return results
    }

    private fun isWordBoundary(text: String, start: Int, end: Int): Boolean {
        // Check character immediately before start
        val startOk = start == 0 || !text[start - 1].isLetterOrDigit()
        // Check character immediately after end
        val endOk = end >= text.length || !text[end].isLetterOrDigit()
        return startOk && endOk
    }
}
