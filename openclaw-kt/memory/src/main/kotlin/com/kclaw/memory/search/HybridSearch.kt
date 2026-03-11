package com.kclaw.memory.search

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Hybrid search combining vector (semantic) and BM25 (keyword) search.
 * Runs both concurrently, normalizes scores to [0,1], merges with configurable weights.
 */
class HybridSearch(
    private val vectorSearch: VectorSearch,
    private val bm25Search: BM25Search,
    private val vectorWeight: Float = 0.7f,
    private val bm25Weight: Float = 0.3f
) {
    /**
     * Search using both vector and BM25, merge and deduplicate results.
     */
    suspend fun search(query: String, limit: Int = 5): List<MemoryResult> = coroutineScope {
        // Fetch more from each sub-search to improve merge quality
        val fetchLimit = limit * 3

        val vectorDeferred = async { vectorSearch.search(query, fetchLimit) }
        val bm25Deferred = async { bm25Search.search(query, fetchLimit) }

        val vectorResults = vectorDeferred.await()
        val bm25Results = bm25Deferred.await()

        val normalizedVector = normalize(vectorResults).map { (result, score) ->
            ChunkKey(result.chunk.sourcePath, result.chunk.chunkIndex) to
                    ScoredResult(result, score * vectorWeight, 0f)
        }

        val normalizedBm25 = normalize(bm25Results).map { (result, score) ->
            ChunkKey(result.chunk.sourcePath, result.chunk.chunkIndex) to
                    ScoredResult(result, 0f, score * bm25Weight)
        }

        // Merge by chunk key, combining scores
        val merged = mutableMapOf<ChunkKey, ScoredResult>()
        for ((key, scored) in normalizedVector) {
            merged[key] = scored
        }
        for ((key, scored) in normalizedBm25) {
            val existing = merged[key]
            if (existing != null) {
                merged[key] = ScoredResult(
                    result = existing.result,
                    vectorScore = existing.vectorScore,
                    bm25Score = scored.bm25Score
                )
            } else {
                merged[key] = scored
            }
        }

        merged.values
            .map { scored ->
                scored.result.copy(score = scored.vectorScore + scored.bm25Score)
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    companion object {
        /**
         * Min-max normalize scores to [0, 1].
         */
        fun normalize(results: List<MemoryResult>): List<Pair<MemoryResult, Float>> {
            if (results.isEmpty()) return emptyList()
            if (results.size == 1) return listOf(results[0] to 1f)

            val min = results.minOf { it.score }
            val max = results.maxOf { it.score }
            val range = max - min

            return if (range == 0f) {
                results.map { it to 1f }
            } else {
                results.map { it to (it.score - min) / range }
            }
        }
    }

    private data class ChunkKey(val sourcePath: String, val chunkIndex: Int)
    private data class ScoredResult(val result: MemoryResult, val vectorScore: Float, val bm25Score: Float)
}
