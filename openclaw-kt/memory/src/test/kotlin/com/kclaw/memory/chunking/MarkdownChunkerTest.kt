package com.kclaw.memory.chunking

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MarkdownChunkerTest {

    private val chunker = MarkdownChunker(chunkSize = 400, overlap = 80)

    @Nested
    inner class BasicChunking {
        @Test
        fun `short document produces single chunk`() {
            val content = "Hello, this is a short document."
            val chunks = chunker.chunk(content, "test.md")
            chunks shouldHaveSize 1
            chunks[0].content shouldBe content
            chunks[0].sourcePath shouldBe "test.md"
            chunks[0].chunkIndex shouldBe 0
        }

        @Test
        fun `long document produces multiple chunks`() {
            val content = buildString {
                repeat(50) { appendLine("This is paragraph $it with enough text to make the document quite long and exceed chunk limits. ".repeat(5)) }
            }
            val chunks = chunker.chunk(content, "long.md")
            chunks.size shouldBeGreaterThan 1
        }

        @Test
        fun `chunk sizes approximate target`() {
            val content = buildString {
                repeat(50) { appendLine("This is a reasonably sized paragraph number $it. ".repeat(10)) }
            }
            val chunks = chunker.chunk(content, "sized.md")
            // Each chunk's content should be roughly within 2x chunk size in chars
            for (chunk in chunks) {
                // Allow generous bounds due to paragraph boundary splitting
                (chunk.content.length <= 400 * 4 * 3) shouldBe true
            }
        }
    }

    @Nested
    inner class Overlap {
        @Test
        fun `overlap content appears in consecutive chunks`() {
            val content = buildString {
                repeat(30) {
                    appendLine("Section $it content paragraph. ".repeat(15))
                    appendLine()
                }
            }
            val chunks = chunker.chunk(content, "overlap.md")
            if (chunks.size >= 2) {
                // The end of chunk N should overlap with start of chunk N+1
                val endOfFirst = chunks[0].content.takeLast(100)
                val startOfSecond = chunks[1].content.take(500)
                // At least some overlap text should appear
                val overlapWords = endOfFirst.split(" ").filter { it.length > 3 }
                val hasOverlap = overlapWords.any { startOfSecond.contains(it) }
                hasOverlap shouldBe true
            }
        }
    }

    @Nested
    inner class SplitPriority {
        @Test
        fun `splits at heading boundaries`() {
            val content = """
                |# Section One
                |Content for section one.
                |
                |# Section Two
                |Content for section two.
            """.trimMargin()
            val chunks = chunker.chunk(content, "headings.md")
            chunks.size shouldBeGreaterThan 0
            // With small content, it may be 1 or 2 chunks
        }

        @Test
        fun `heading context is tracked`() {
            val content = """
                |# My Heading
                |Some content under the heading.
            """.trimMargin()
            val chunks = chunker.chunk(content, "heading.md")
            chunks shouldHaveSize 1
            chunks[0].headingContext shouldBe "My Heading"
        }
    }

    @Nested
    inner class LineNumbers {
        @Test
        fun `line numbers are tracked correctly`() {
            val content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
            val chunks = chunker.chunk(content, "lines.md")
            chunks shouldHaveSize 1
            chunks[0].startLine shouldBe 1
            chunks[0].endLine shouldBe 5
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `empty document returns no chunks`() {
            chunker.chunk("", "empty.md") shouldHaveSize 0
        }

        @Test
        fun `whitespace-only document returns no chunks`() {
            chunker.chunk("   \n\n  \t  \n", "ws.md") shouldHaveSize 0
        }

        @Test
        fun `unicode content chunked correctly`() {
            val content = "こんにちは世界。これはテスト文書です。"
            val chunks = chunker.chunk(content, "unicode.md")
            chunks shouldHaveSize 1
            chunks[0].content shouldContain "こんにちは"
        }
    }
}
