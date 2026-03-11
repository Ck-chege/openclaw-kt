package com.kclaw.memory.search

import com.kclaw.memory.chunking.Chunk
import com.kclaw.memory.embedding.EmbeddingProvider
import com.kclaw.memory.indexing.StoredEntry
import com.kclaw.memory.indexing.VectorStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual as floatGte
import io.kotest.matchers.floats.shouldBeLessThanOrEqual as floatLte
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HybridSearchTest {

    private fun chunk(content: String, index: Int = 0) = Chunk(
        sourcePath = "test.md", chunkIndex = index, content = content,
        tokenCount = content.length / 4, startLine = 1, endLine = 1
    )

    @Nested
    inner class Normalization {
        @Test
        fun `single result normalizes to 1`() {
            val result = MemoryResult(chunk("test"), 0.5f, "snippet", "test.md")
            val normalized = HybridSearch.normalize(listOf(result))
            normalized shouldHaveSize 1
            normalized[0].second shouldBe 1f
        }

        @Test
        fun `empty list returns empty`() {
            HybridSearch.normalize(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `all same scores normalize to 1`() {
            val results = (0 until 3).map {
                MemoryResult(chunk("test", it), 0.5f, "snippet", "test.md")
            }
            val normalized = HybridSearch.normalize(results)
            normalized.forEach { it.second shouldBe 1f }
        }

        @Test
        fun `min-max normalization to 0-1 range`() {
            val results = listOf(
                MemoryResult(chunk("low", 0), 0.2f, "s", "t"),
                MemoryResult(chunk("mid", 1), 0.5f, "s", "t"),
                MemoryResult(chunk("high", 2), 0.8f, "s", "t")
            )
            val normalized = HybridSearch.normalize(results)
            normalized.forEach { (_, score) ->
                score floatGte 0f
                score floatLte 1f
            }
            // Lowest should be 0, highest should be 1
            val scores = normalized.sortedBy { it.first.chunk.chunkIndex }.map { it.second }
            scores[0] shouldBe 0f
            scores[2] shouldBe 1f
        }
    }

    @Nested
    inner class Deduplication {
        @Test
        fun `deduplication merges same chunk from both searches`() = runTest {
            val store = VectorStore()
            store.addAll(listOf(
                StoredEntry(chunk("kotlin coroutines guide", 0), listOf(1f, 0f, 0f)),
                StoredEntry(chunk("java threading model", 1), listOf(0f, 1f, 0f))
            ))
            val provider = object : EmbeddingProvider {
                override suspend fun embed(texts: List<String>) = texts.map { floatArrayOf(1f, 0f, 0f) }
                override val dimensions = 3
                override val providerName = "mock"
            }
            val vectorSearch = VectorSearch(store, provider)
            val bm25Search = BM25Search(store)
            val hybrid = HybridSearch(vectorSearch, bm25Search)

            val results = hybrid.search("kotlin coroutines")
            // Both searches should find "kotlin coroutines guide", but it should appear only once
            val kotlinResults = results.filter { it.chunk.content.contains("kotlin") }
            kotlinResults shouldHaveSize 1
        }
    }

    @Nested
    inner class WeightedCombination {
        @Test
        fun `respects vector and bm25 weights`() = runTest {
            val store = VectorStore()
            store.addAll(listOf(
                StoredEntry(chunk("vector match content", 0), listOf(1f, 0f, 0f)),
                StoredEntry(chunk("keyword keyword keyword", 1), listOf(0f, 0f, 1f))
            ))
            val provider = object : EmbeddingProvider {
                override suspend fun embed(texts: List<String>) = texts.map { floatArrayOf(1f, 0f, 0f) }
                override val dimensions = 3
                override val providerName = "mock"
            }
            val vectorSearch = VectorSearch(store, provider)
            val bm25Search = BM25Search(store)
            val hybrid = HybridSearch(vectorSearch, bm25Search, vectorWeight = 0.7f, bm25Weight = 0.3f)

            val results = hybrid.search("keyword")
            // Both should appear — keyword match via BM25, vector match via cosine
            results.size shouldBeGreaterThanOrEqual 1
        }

        @Test
        fun `end-to-end with mock provider returns results`() = runTest {
            val store = VectorStore()
            store.addAll(listOf(
                StoredEntry(chunk("authentication login flow", 0), listOf(0.9f, 0.1f)),
                StoredEntry(chunk("database migration guide", 1), listOf(0.1f, 0.9f)),
                StoredEntry(chunk("authentication security", 2), listOf(0.8f, 0.2f))
            ))
            val provider = object : EmbeddingProvider {
                override suspend fun embed(texts: List<String>) = texts.map { floatArrayOf(0.9f, 0.1f) }
                override val dimensions = 2
                override val providerName = "mock"
            }
            val vectorSearch = VectorSearch(store, provider)
            val bm25Search = BM25Search(store)
            val hybrid = HybridSearch(vectorSearch, bm25Search)

            val results = hybrid.search("authentication")
            results.size shouldBeGreaterThanOrEqual 1
            // Auth-related docs should rank higher than database
            results[0].chunk.content shouldContain "authentication"
        }
    }
}
