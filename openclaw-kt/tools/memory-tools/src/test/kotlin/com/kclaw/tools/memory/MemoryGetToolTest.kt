package com.kclaw.tools.memory

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*

class MemoryGetToolTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        MemoryGetTool.workspacePath = tempDir
    }

    @Nested
    inner class HappyPath {
        @Test
        fun `reads existing file`() = runTest {
            tempDir.resolve("hello.md").writeText("Hello World!")
            val output = MemoryGetTool.execute(MemoryGetTool.Args("hello.md"))
            output shouldContain "Hello World!"
        }

        @Test
        fun `reads file in subdirectory`() = runTest {
            tempDir.resolve("sub").createDirectories()
            tempDir.resolve("sub/doc.txt").writeText("Nested content")
            val output = MemoryGetTool.execute(MemoryGetTool.Args("sub/doc.txt"))
            output shouldContain "Nested content"
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `error for nonexistent file`() = runTest {
            val output = MemoryGetTool.execute(MemoryGetTool.Args("nope.md"))
            output shouldContain "not found"
        }

        @Test
        fun `error for directory`() = runTest {
            tempDir.resolve("adir").createDirectories()
            val output = MemoryGetTool.execute(MemoryGetTool.Args("adir"))
            output shouldContain "directory"
        }

        @Test
        fun `error for empty path`() = runTest {
            val output = MemoryGetTool.execute(MemoryGetTool.Args(""))
            output shouldContain "cannot be empty"
        }
    }

    @Nested
    inner class Truncation {
        @Test
        fun `truncates to maxChars`() = runTest {
            tempDir.resolve("big.md").writeText("A".repeat(5000))
            val output = MemoryGetTool.execute(MemoryGetTool.Args("big.md", maxChars = 200))
            output shouldContain "truncated at 200"
        }
    }

    @Nested
    inner class PathTraversal {
        @Test
        fun `blocks path traversal with dotdot`() = runTest {
            val output = MemoryGetTool.execute(MemoryGetTool.Args("../etc/passwd"))
            output shouldContain "not allowed"
        }

        @Test
        fun `blocks absolute path`() = runTest {
            val output = MemoryGetTool.execute(MemoryGetTool.Args("/etc/passwd"))
            output shouldContain "not allowed"
        }

        @Test
        fun `blocks embedded dotdot`() = runTest {
            val output = MemoryGetTool.execute(MemoryGetTool.Args("sub/../../etc/passwd"))
            output shouldContain "not allowed"
        }
    }
}
