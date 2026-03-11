package com.kclaw.tools.memory

import com.kclaw.memory.MemoryFacade
import com.kclaw.memory.MemoryStatus
import com.kclaw.memory.chunking.Chunk
import com.kclaw.memory.indexing.IndexStats
import com.kclaw.memory.search.MemoryResult
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class MemorySearchToolTest {

    @AfterEach
    fun tearDown() {
        MemorySearchTool.memoryManager = null
    }

    private fun mockFacade(results: List<MemoryResult> = emptyList()): MemoryFacade = object : MemoryFacade {
        override val isEnabled = true
        override suspend fun search(query: String, limit: Int) = results.take(limit)
        override suspend fun index(paths: List<Path>?) = IndexStats(0, 0, 0, 0, 0)
        override fun status() = MemoryStatus(true, false, 0, 0, "mock", 3, 0)
    }

    private fun result(content: String = "test content", score: Float = 0.9f) = MemoryResult(
        chunk = Chunk("test.md", 0, content, content.length / 4, 1, 1, null),
        score = score,
        snippet = "[test.md:L1-L1]\n$content",
        source = "test.md"
    )

    @Nested
    inner class HappyPath {
        @Test
        fun `returns formatted results`() = runTest {
            MemorySearchTool.memoryManager = mockFacade(listOf(result("relevant doc", 0.95f)))
            val output = MemorySearchTool.execute(MemorySearchTool.Args("test query"))
            output shouldContain "Found 1 result"
            output shouldContain "relevant doc"
            output shouldContain "0.950"
        }

        @Test
        fun `empty results`() = runTest {
            MemorySearchTool.memoryManager = mockFacade(emptyList())
            val output = MemorySearchTool.execute(MemorySearchTool.Args("no match"))
            output shouldContain "No results found"
        }
    }

    @Nested
    inner class DisabledMemory {
        @Test
        fun `null manager returns disabled message`() = runTest {
            MemorySearchTool.memoryManager = null
            val output = MemorySearchTool.execute(MemorySearchTool.Args("test"))
            output shouldContain "not enabled"
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `blank query returns error`() = runTest {
            MemorySearchTool.memoryManager = mockFacade()
            val output = MemorySearchTool.execute(MemorySearchTool.Args("   "))
            output shouldContain "cannot be empty"
        }

        @Test
        fun `limit is respected`() = runTest {
            val results = (0 until 10).map { result("doc $it", 0.9f - it * 0.01f) }
            MemorySearchTool.memoryManager = mockFacade(results)
            val output = MemorySearchTool.execute(MemorySearchTool.Args("test", limit = 3))
            output shouldContain "Found 3 result"
        }
    }
}
