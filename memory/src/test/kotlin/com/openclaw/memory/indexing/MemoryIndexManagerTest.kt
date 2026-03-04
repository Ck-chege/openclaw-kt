package com.openclaw.memory.indexing

import com.openclaw.memory.chunking.MarkdownChunker
import com.openclaw.memory.embedding.EmbeddingCache
import com.openclaw.memory.embedding.EmbeddingProvider
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*

class MemoryIndexManagerTest {

    @TempDir
    lateinit var tempDir: Path

    /** Mock embedding provider that returns deterministic vectors. */
    private class MockEmbeddingProvider : EmbeddingProvider {
        var callCount = 0
        var lastTexts: List<String> = emptyList()

        override suspend fun embed(texts: List<String>): List<FloatArray> {
            callCount++
            lastTexts = texts
            return texts.map { text ->
                FloatArray(3) { i -> (text.hashCode().toFloat() + i) / 1000f }
            }
        }

        override val dimensions = 3
        override val providerName = "mock"
    }

    private fun createManager(
        workspace: Path = tempDir,
        provider: MockEmbeddingProvider = MockEmbeddingProvider()
    ): Pair<MemoryIndexManager, MockEmbeddingProvider> {
        val cache = EmbeddingCache(tempDir.resolve(".memory/cache"))
        val store = VectorStore()
        val chunker = MarkdownChunker()
        return MemoryIndexManager(provider, cache, store, chunker, workspace) to provider
    }

    @Nested
    inner class ScanFiles {
        @Test
        fun `finds md and txt files`() {
            tempDir.resolve("doc.md").writeText("# Hello")
            tempDir.resolve("notes.txt").writeText("Notes")
            tempDir.resolve("code.kt").writeText("fun main() {}")
            val (manager) = createManager()
            val files = manager.scanFiles(tempDir)
            files shouldHaveSize 2
        }

        @Test
        fun `ignores hidden directories`() {
            tempDir.resolve(".hidden").createDirectories()
            tempDir.resolve(".hidden/secret.md").writeText("secret")
            tempDir.resolve("visible.md").writeText("visible")
            val (manager) = createManager()
            manager.scanFiles(tempDir) shouldHaveSize 1
        }

        @Test
        fun `recurses into subdirectories`() {
            tempDir.resolve("sub/nested").createDirectories()
            tempDir.resolve("sub/nested/deep.md").writeText("deep")
            tempDir.resolve("root.md").writeText("root")
            val (manager) = createManager()
            manager.scanFiles(tempDir) shouldHaveSize 2
        }

        @Test
        fun `ignores binary files`() {
            tempDir.resolve("image.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            tempDir.resolve("doc.md").writeText("doc")
            val (manager) = createManager()
            manager.scanFiles(tempDir) shouldHaveSize 1
        }
    }

    @Nested
    inner class Indexing {
        @Test
        fun `end-to-end index produces correct stats`() = runTest {
            tempDir.resolve("doc.md").writeText("# Hello\nThis is test content.")
            val (manager, provider) = createManager()
            val files = manager.scanFiles(tempDir)
            val stats = manager.index(files)
            stats.filesScanned shouldBe files.size
            stats.chunksCreated shouldBeGreaterThan 0
            stats.embeddingsComputed shouldBeGreaterThan 0
        }

        @Test
        fun `cache reuse on re-index`() = runTest {
            tempDir.resolve("doc.md").writeText("# Hello\nContent here.")
            val provider = MockEmbeddingProvider()
            val cache = EmbeddingCache(tempDir.resolve(".memory/cache"))
            val store = VectorStore()
            val chunker = MarkdownChunker()
            val manager = MemoryIndexManager(provider, cache, store, chunker, tempDir)

            val files = manager.scanFiles(tempDir)
            val stats1 = manager.index(files)
            stats1.embeddingsComputed shouldBeGreaterThan 0
            stats1.embeddingsCached shouldBe 0

            // Re-index — should use cache
            val stats2 = manager.index(files)
            stats2.embeddingsCached shouldBe stats2.chunksCreated
            stats2.embeddingsComputed shouldBe 0
        }

        @Test
        fun `modified file is re-indexed`() = runTest {
            val file = tempDir.resolve("doc.md")
            file.writeText("Original content.")
            val provider = MockEmbeddingProvider()
            val cache = EmbeddingCache(tempDir.resolve(".memory/cache"))
            val store = VectorStore()
            val chunker = MarkdownChunker()
            val manager = MemoryIndexManager(provider, cache, store, chunker, tempDir)

            val files = manager.scanFiles(tempDir)
            manager.index(files)

            // Modify file
            file.writeText("Modified content that is different.")
            val stats2 = manager.index(files)
            stats2.embeddingsComputed shouldBeGreaterThan 0
        }
    }
}
