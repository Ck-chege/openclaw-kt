package com.kclaw.memory.search

import com.kclaw.memory.indexing.VectorStore
import kotlin.math.ln

/**
 * BM25 keyword search over the memory store.
 * Simple implementation: tokenize on non-alphanumeric, no stemming/stopwords.
 */
class BM25Search(
    private val vectorStore: VectorStore,
    private val k1: Float = 1.2f,
    private val b: Float = 0.75f
) {
    fun search(query: String, limit: Int = 5): List<MemoryResult> {
        val entries = vectorStore.allEntries()
        if (entries.isEmpty()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        // Tokenize all documents
        val docTokens = entries.map { tokenize(it.chunk.content) }
        val avgDl = docTokens.map { it.size.toFloat() }.average().toFloat()
        val n = entries.size.toFloat()

        // Compute IDF for each query term
        val idf = mutableMapOf<String, Float>()
        for (term in queryTerms) {
            val df = docTokens.count { tokens -> term in tokens }.toFloat()
            // IDF with smoothing to avoid negative values
            idf[term] = ln(((n - df + 0.5f) / (df + 0.5f) + 1f).toDouble()).toFloat()
        }

        // Score each document
        return entries.mapIndexed { i, entry ->
            val tokens = docTokens[i]
            val dl = tokens.size.toFloat()
            val termFreq = tokens.groupingBy { it }.eachCount()

            var score = 0f
            for (term in queryTerms) {
                val tf = (termFreq[term] ?: 0).toFloat()
                val termIdf = idf[term] ?: 0f
                score += termIdf * ((tf * (k1 + 1f)) / (tf + k1 * (1f - b + b * dl / avgDl)))
            }

            MemoryResult(
                chunk = entry.chunk,
                score = score,
                snippet = SnippetFormatter.format(entry.chunk),
                source = entry.chunk.sourcePath
            )
        }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(limit)
    }

    companion object {
        /** Tokenize text: lowercase, split on non-alphanumeric characters. */
        fun tokenize(text: String): List<String> =
            text.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
    }
}
