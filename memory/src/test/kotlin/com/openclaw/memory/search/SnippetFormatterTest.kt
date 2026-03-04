package com.openclaw.memory.search

import com.openclaw.memory.chunking.Chunk
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SnippetFormatterTest {

    private fun chunk(
        content: String = "Some content",
        sourcePath: String = "docs/readme.md",
        startLine: Int = 1,
        endLine: Int = 10,
        heading: String? = null
    ) = Chunk(
        sourcePath = sourcePath,
        chunkIndex = 0,
        content = content,
        tokenCount = content.length / 4,
        startLine = startLine,
        endLine = endLine,
        headingContext = heading
    )

    @Nested
    inner class Formatting {
        @Test
        fun `includes path and line range`() {
            val snippet = SnippetFormatter.format(chunk())
            snippet shouldContain "[docs/readme.md:L1-L10]"
        }

        @Test
        fun `includes heading context when present`() {
            val snippet = SnippetFormatter.format(chunk(heading = "Architecture"))
            snippet shouldContain "Architecture"
        }

        @Test
        fun `no heading context when absent`() {
            val snippet = SnippetFormatter.format(chunk(heading = null))
            snippet shouldContain "[docs/readme.md:L1-L10]\n"
        }
    }

    @Nested
    inner class Truncation {
        @Test
        fun `long content is truncated at 700 chars`() {
            val longContent = "A".repeat(1000)
            val snippet = SnippetFormatter.format(chunk(content = longContent))
            snippet.length shouldBeLessThanOrEqual 700
            snippet shouldEndWith "..."
        }

        @Test
        fun `short content is not truncated`() {
            val snippet = SnippetFormatter.format(chunk(content = "Short content"))
            (snippet.endsWith("...")) shouldBe false
            snippet shouldContain "Short content"
        }
    }
}
