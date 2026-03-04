package com.openclaw.memory.search

import com.openclaw.memory.chunking.Chunk
import com.openclaw.memory.embedding.EmbeddingProvider
import com.openclaw.memory.indexing.StoredEntry
import com.openclaw.memory.indexing.VectorStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VectorSearchTest {

    private class MockEmbeddingProvider(private val queryVector: FloatArray) : EmbeddingProvider {
        override suspend fun embed(texts: List<String>): List<FloatArray> =
            texts.map { queryVector }
        override val dimensions = queryVector.size
        override val providerName = "mock"
    }

    private fun chunk(source: String = "test.md", index: Int = 0, content: String = "test") = Chunk(
        sourcePath = source, chunkIndex = index, content = content,
        tokenCount = content.length / 4, startLine = 1, endLine = 1
    )

    private fun entry(embedding: FloatArray, source: String = "test.md", index: Int = 0, content: String = "test") =
        StoredEntry(chunk(source, index, content), embedding.toList())

    @Nested
    inner class SearchResults {
        @Test
        fun `sorted by similarity descending`() = runTest {
            val queryVec = floatArrayOf(1f, 0f, 0f)
            val store = VectorStore()
            store.addAll(listOf(
                entry(floatArrayOf(0f, 1f, 0f), index = 0, content = "orthogonal"),
                entry(floatArrayOf(1f, 0f, 0f), index = 1, content = "exact match"),
                entry(floatArrayOf(0.5f, 0.5f, 0f), index = 2, content = "partial")
            ))
            val search = VectorSearch(store, MockEmbeddingProvider(queryVec))
            val results = search.search("query", limit = 3)
            results shouldHaveSize 3
            results[0].chunk.content shouldBe "exact match"
            results[0].score shouldBeGreaterThan results[1].score
            results[1].score shouldBeGreaterThan results[2].score
        }

        @Test
        fun `respects limit`() = runTest {
            val queryVec = floatArrayOf(1f, 0f)
            val store = VectorStore()
            store.addAll((0 until 10).map {
                entry(floatArrayOf(1f, 0f), index = it)
            })
            val search = VectorSearch(store, MockEmbeddingProvider(queryVec))
            val results = search.search("query", limit = 3)
            results shouldHaveSize 3
        }

        @Test
        fun `empty store returns empty results`() = runTest {
            val store = VectorStore()
            val search = VectorSearch(store, MockEmbeddingProvider(floatArrayOf(1f)))
            search.search("query").shouldBeEmpty()
        }

        @Test
        fun `exact match has highest score`() = runTest {
            val queryVec = floatArrayOf(1f, 0f, 0f)
            val store = VectorStore()
            store.addAll(listOf(
                entry(floatArrayOf(1f, 0f, 0f), index = 0, content = "exact"),
                entry(floatArrayOf(0.7f, 0.7f, 0f), index = 1, content = "similar")
            ))
            val search = VectorSearch(store, MockEmbeddingProvider(queryVec))
            val results = search.search("query")
            results[0].chunk.content shouldBe "exact"
        }
    }
}
