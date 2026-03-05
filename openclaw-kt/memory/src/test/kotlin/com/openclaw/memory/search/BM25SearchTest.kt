package com.openclaw.memory.search

import com.openclaw.memory.chunking.Chunk
import com.openclaw.memory.indexing.StoredEntry
import com.openclaw.memory.indexing.VectorStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BM25SearchTest {

    private fun chunk(content: String, index: Int = 0) = Chunk(
        sourcePath = "test.md", chunkIndex = index, content = content,
        tokenCount = content.length / 4, startLine = 1, endLine = 1
    )

    private fun entry(content: String, index: Int = 0) =
        StoredEntry(chunk(content, index), listOf(0f))

    @Nested
    inner class Tokenization {
        @Test
        fun `splits on whitespace`() {
            BM25Search.tokenize("hello world") shouldBe listOf("hello", "world")
        }

        @Test
        fun `lowercases tokens`() {
            BM25Search.tokenize("Hello WORLD") shouldBe listOf("hello", "world")
        }

        @Test
        fun `removes punctuation`() {
            BM25Search.tokenize("hello, world! foo.") shouldBe listOf("hello", "world", "foo")
        }

        @Test
        fun `handles unicode`() {
            val tokens = BM25Search.tokenize("こんにちは world")
            tokens shouldHaveSize 2
            tokens[0] shouldBe "こんにちは"
        }

        @Test
        fun `empty string produces empty list`() {
            BM25Search.tokenize("").shouldBeEmpty()
        }

        @Test
        fun `whitespace only produces empty list`() {
            BM25Search.tokenize("   \t\n  ").shouldBeEmpty()
        }
    }

    @Nested
    inner class SearchResults {
        @Test
        fun `returns documents containing query terms`() {
            val store = VectorStore()
            store.addAll(listOf(
                entry("The cat sat on the mat", 0),
                entry("Dogs run in the park", 1),
                entry("The quick brown fox", 2)
            ))
            val search = BM25Search(store)
            val results = search.search("cat mat")
            results shouldHaveSize 1
            results[0].chunk.content shouldBe "The cat sat on the mat"
        }

        @Test
        fun `exact term match ranks higher than partial`() {
            val store = VectorStore()
            store.addAll(listOf(
                entry("Kotlin is a programming language for the JVM", 0),
                entry("Kotlin Kotlin Kotlin everywhere Kotlin", 1)
            ))
            val search = BM25Search(store)
            val results = search.search("kotlin")
            results shouldHaveSize 2
            // Doc with more occurrences should rank higher
            results[0].chunk.chunkIndex shouldBe 1
        }

        @Test
        fun `multiple query terms rank higher`() {
            val store = VectorStore()
            store.addAll(listOf(
                entry("apples are fruits", 0),
                entry("apples and bananas are fruits", 1)
            ))
            val search = BM25Search(store)
            val results = search.search("apples bananas")
            results shouldHaveSize 2
            results[0].chunk.chunkIndex shouldBe 1
        }

        @Test
        fun `rare terms get higher IDF boost`() {
            val store = VectorStore()
            store.addAll(listOf(
                entry("the common word appears everywhere", 0),
                entry("the common word appears everywhere", 1),
                entry("the common word appears everywhere", 2),
                entry("unique specialized terminology here", 3)
            ))
            val search = BM25Search(store)
            val results = search.search("unique")
            results shouldHaveSize 1
            results[0].score shouldBeGreaterThan 0f
        }

        @Test
        fun `empty store returns empty results`() {
            val store = VectorStore()
            val search = BM25Search(store)
            search.search("query").shouldBeEmpty()
        }
    }
}
