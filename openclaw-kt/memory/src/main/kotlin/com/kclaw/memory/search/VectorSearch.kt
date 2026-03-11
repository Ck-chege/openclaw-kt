package com.kclaw.memory.search

import com.kclaw.memory.embedding.EmbeddingProvider
import com.kclaw.memory.embedding.cosineSimilarity
import com.kclaw.memory.indexing.VectorStore

/**
 * Vector (semantic) search over the memory store.
 * Embeds the query and computes cosine similarity against all stored vectors.
 */
class VectorSearch(
    private val vectorStore: VectorStore,
    private val embeddingProvider: EmbeddingProvider
) {
    suspend fun search(query: String, limit: Int = 5): List<MemoryResult> {
        val entries = vectorStore.allEntries()
        if (entries.isEmpty()) return emptyList()

        val queryEmbedding = embeddingProvider.embed(listOf(query)).first()

        return entries
            .map { entry ->
                val similarity = queryEmbedding.cosineSimilarity(entry.embeddingArray())
                MemoryResult(
                    chunk = entry.chunk,
                    score = similarity,
                    snippet = SnippetFormatter.format(entry.chunk),
                    source = entry.chunk.sourcePath
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
