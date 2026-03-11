package com.kclaw.memory.indexing

import com.kclaw.memory.chunking.Chunk
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class VectorStoreTest {

    private fun chunk(source: String = "test.md", index: Int = 0, content: String = "test content") = Chunk(
        sourcePath = source,
        chunkIndex = index,
        content = content,
        tokenCount = content.length / 4,
        startLine = 1,
        endLine = 1
    )

    private fun entry(source: String = "test.md", index: Int = 0, content: String = "test content") =
        StoredEntry(chunk(source, index, content), listOf(0.1f, 0.2f, 0.3f))

    @Nested
    inner class BasicOperations {
        @Test
        fun `addAll and allEntries`() {
            val store = VectorStore()
            store.addAll(listOf(entry("a.md"), entry("b.md")))
            store.allEntries() shouldHaveSize 2
        }

        @Test
        fun `entriesBySource filters correctly`() {
            val store = VectorStore()
            store.addAll(listOf(entry("a.md"), entry("b.md"), entry("a.md", index = 1)))
            store.entriesBySource("a.md") shouldHaveSize 2
            store.entriesBySource("b.md") shouldHaveSize 1
            store.entriesBySource("c.md") shouldHaveSize 0
        }

        @Test
        fun `removeBySource removes matching entries`() {
            val store = VectorStore()
            store.addAll(listOf(entry("a.md"), entry("b.md"), entry("a.md", index = 1)))
            store.removeBySource("a.md")
            store.allEntries() shouldHaveSize 1
            store.allEntries()[0].chunk.sourcePath shouldBe "b.md"
        }

        @Test
        fun `size returns correct count`() {
            val store = VectorStore()
            store.size() shouldBe 0
            store.addAll(listOf(entry(), entry(index = 1)))
            store.size() shouldBe 2
        }

        @Test
        fun `clear removes all entries`() {
            val store = VectorStore()
            store.addAll(listOf(entry(), entry(index = 1)))
            store.clear()
            store.size() shouldBe 0
        }
    }

    @Nested
    inner class Persistence {
        @TempDir
        lateinit var tempDir: Path

        @Test
        fun `save and load round-trip`() {
            val store = VectorStore()
            store.addAll(listOf(entry("a.md"), entry("b.md")))
            val path = tempDir.resolve("index.json")
            store.saveToDisk(path)

            val loaded = VectorStore()
            loaded.loadFromDisk(path) shouldBe true
            loaded.allEntries() shouldHaveSize 2
            loaded.allEntries()[0].chunk.sourcePath shouldBe "a.md"
        }

        @Test
        fun `load from nonexistent returns false`() {
            val store = VectorStore()
            store.loadFromDisk(tempDir.resolve("nope.json")) shouldBe false
            store.size() shouldBe 0
        }
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `concurrent addAll does not lose entries`() {
            val store = VectorStore()
            val threads = (0 until 10).map { threadIdx ->
                Thread {
                    store.addAll((0 until 100).map { entry(index = threadIdx * 100 + it) })
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            store.size() shouldBe 1000
        }
    }
}
