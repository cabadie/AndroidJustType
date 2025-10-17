package com.justtype.nativeapp.logic

import java.util.Locale

class WLD(
    private val lettersPerKey: List<String> = listOf("me'gz", "trp-", "iskw", "lufcy", "banq", "ojhdvx")
) {
    private val symbolKeys = 6
    private val letterToKey = IntArray(256) { -1 }

    init {
        var keyNumber = 0
        lettersPerKey.forEach { keys ->
            keys.forEach { ch ->
                val lower = ch.lowercaseChar().code
                val upper = ch.uppercaseChar().code
                letterToKey[lower] = keyNumber
                letterToKey[upper] = keyNumber
            }
            keyNumber += 1
        }
    }

    private data class WordEntry(
        val output: String,
        val display: String?,
        val count: Int,
        val pos: String?
    )

    // Trie nodes
    private data class NextNode(var index: Int, var count: Int)
    private data class Node(
        var exact: MutableList<WordEntry>? = null,
        var next: Array<NextNode?>? = null,
    )

    private val db = mutableListOf(Node())

    fun addWords(lines: List<String>, avoid: Set<String>) {
        for (line in lines) {
            val fields = line.trim().split(';')
            if (fields.size < 2) continue
            val symbolSequence = fields[0]
            if (avoid.contains(symbolSequence.lowercase(Locale.getDefault()))) continue
            val count = fields.getOrNull(1)?.toIntOrNull() ?: continue
            val pos = fields.getOrNull(2)
            val output = fields.getOrNull(3) ?: symbolSequence
            val display = fields.getOrNull(4)

            val keys = translateToKeys(symbolSequence) ?: continue

            var nodeIndex = 0
            keys.forEachIndexed { idx, key ->
                val node = db[nodeIndex]
                if (node.next == null) node.next = arrayOfNulls(symbolKeys)
                if (node.next!![key] == null) {
                    db.add(Node())
                    node.next!![key] = NextNode(db.lastIndex, 0)
                }
                node.next!![key]!!.count += count
                nodeIndex = node.next!![key]!!.index
                if (idx == keys.lastIndex) {
                    val word = WordEntry(output, display, count, pos)
                    val exact = db[nodeIndex].exact
                    if (exact == null) db[nodeIndex].exact = mutableListOf(word) else exact.add(word)
                }
            }
        }
    }

    fun addCustomWord(word: String) {
        val count = 1000
        val pos: String? = "NNP"
        val display: String? = null
        val keys = translateToKeys(word) ?: return
        var nodeIndex = 0
        keys.forEachIndexed { idx, key ->
            val node = db[nodeIndex]
            if (node.next == null) node.next = arrayOfNulls(symbolKeys)
            if (node.next!![key] == null) {
                db.add(Node())
                node.next!![key] = NextNode(db.lastIndex, 0)
            }
            node.next!![key]!!.count += count
            nodeIndex = node.next!![key]!!.index
            if (idx == keys.lastIndex) {
                val exact = db[nodeIndex].exact
                val we = WordEntry(word, null, count, pos)
                if (exact == null) db[nodeIndex].exact = mutableListOf(we) else exact.add(we)
            }
        }
    }

    private fun translateToKeys(symbolSequence: String): List<Int>? {
        val out = mutableListOf<Int>()
        for (ch in symbolSequence) {
            val k = letterToKey.getOrNull(ch.code) ?: -1
            if (k == -1) return null
            out.add(k)
        }
        return out
    }

    fun getDisambiguationList(keys: List<Int>, maxWordCompleteEntries: Int = 10): List<Map<String, Any?>> {
        var nodeIndex = 0
        for (k in keys) {
            val node = db[nodeIndex]
            val next = node.next ?: return emptyList()
            val nn = next.getOrNull(k) ?: return emptyList()
            nodeIndex = nn.index
        }
        val result = mutableListOf<Map<String, Any?>>()
        val node = db[nodeIndex]
        node.exact?.forEach { w ->
            result.add(mapOf("type" to "X", "display" to w.display, "output" to w.output, "countOfOccurrence" to w.count, "POS" to (w.pos ?: "")))
        }
        // Add longer words via DFS up to some limit
        val wcList = mutableListOf<WordEntry>()
        fun dfs(nIdx: Int, first: Boolean) {
            val n = db[nIdx]
            if (!first) {
                n.exact?.forEach { w -> wcList.add(w) }
            }
            val next = n.next ?: return
            next.filterNotNull().sortedByDescending { it.count }.forEach { child ->
                if (wcList.size >= maxWordCompleteEntries) return
                dfs(child.index, false)
            }
        }
        dfs(nodeIndex, true)
        wcList.take(maxWordCompleteEntries).forEach { w ->
            result.add(mapOf("type" to "L", "display" to w.display, "output" to w.output, "countOfOccurrence" to w.count, "POS" to (w.pos ?: "")))
        }
        return result
    }
}
