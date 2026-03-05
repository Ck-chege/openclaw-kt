package com.openclaw.tools.filesystem

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ReadToolTest {

    @TempDir
    lateinit var workspace: Path

    @BeforeEach
    fun setUp() {
        ReadTool.workspacePath = workspace
        ReadTool.enforceBoundary = true
        ReadTool.maxBytes = 50_000
    }

    @Nested
    inner class BasicReading {

        @Test
        fun `reads entire file with line numbers`() = runTest {
            val file = workspace.resolve("hello.txt")
            Files.writeString(file, "line one\nline two\nline three\nline four\nline five\n")

            val result = ReadTool.execute(ReadTool.Args(path = "hello.txt"))

            result shouldContain "     1\tline one"
            result shouldContain "     2\tline two"
            result shouldContain "     5\tline five"
        }

        @Test
        fun `reads with offset starting at given line`() = runTest {
            val file = workspace.resolve("offset.txt")
            Files.writeString(file, "a\nb\nc\nd\ne\n")

            val result = ReadTool.execute(ReadTool.Args(path = "offset.txt", offset = 3))

            result shouldStartWith "     3\tc"
            result shouldContain "     4\td"
            result shouldContain "     5\te"
        }

        @Test
        fun `reads with limit returning only specified lines`() = runTest {
            val file = workspace.resolve("limit.txt")
            Files.writeString(file, "a\nb\nc\nd\ne\n")

            val result = ReadTool.execute(ReadTool.Args(path = "limit.txt", limit = 2))

            result shouldContain "     1\ta"
            result shouldContain "     2\tb"
            // Should NOT contain line 3
            result.contains("     3\t") shouldBe false
        }

        @Test
        fun `reads with offset and limit combined`() = runTest {
            val file = workspace.resolve("combo.txt")
            Files.writeString(file, "a\nb\nc\nd\ne\n")

            val result = ReadTool.execute(ReadTool.Args(path = "combo.txt", offset = 2, limit = 2))

            result shouldContain "     2\tb"
            result shouldContain "     3\tc"
            result.contains("     1\t") shouldBe false
            result.contains("     4\t") shouldBe false
        }
    }

    @Nested
    inner class Truncation {

        @Test
        fun `shows truncation notice when file exceeds maxBytes`() = runTest {
            ReadTool.maxBytes = 100

            val file = workspace.resolve("large.txt")
            val content = (1..100).joinToString("\n") { "This is line number $it with some padding text" }
            Files.writeString(file, content)

            val result = ReadTool.execute(ReadTool.Args(path = "large.txt"))

            result shouldContain "[Read output capped at"
            result shouldContain "Use offset="
        }
    }

    @Nested
    inner class ErrorCases {

        @Test
        fun `returns error for nonexistent file`() = runTest {
            val result = ReadTool.execute(ReadTool.Args(path = "nope.txt"))

            result shouldContain "Error: File not found"
        }

        @Test
        fun `returns error for directory path`() = runTest {
            val dir = workspace.resolve("subdir")
            Files.createDirectories(dir)

            val result = ReadTool.execute(ReadTool.Args(path = "subdir"))

            result shouldContain "Error: Path is a directory"
        }

        @Test
        fun `returns error for binary file`() = runTest {
            val file = workspace.resolve("binary.bin")
            Files.write(file, byteArrayOf(0x48, 0x65, 0x6C, 0x00, 0x6F))

            val result = ReadTool.execute(ReadTool.Args(path = "binary.bin"))

            result shouldContain "Error: File appears to be binary"
        }

        @Test
        fun `returns error when offset exceeds file length`() = runTest {
            val file = workspace.resolve("short.txt")
            Files.writeString(file, "one\ntwo\n")

            val result = ReadTool.execute(ReadTool.Args(path = "short.txt", offset = 10))

            result shouldContain "Error: offset 10 is beyond file length"
        }
    }

    @Nested
    inner class BoundaryEnforcement {

        @Test
        fun `blocks read outside workspace when boundary enforced`() = runTest {
            val result = ReadTool.execute(ReadTool.Args(path = "/etc/passwd"))

            result shouldContain "Error:"
        }

        @Test
        fun `allows read outside workspace when boundary disabled`() = runTest {
            ReadTool.enforceBoundary = false

            // Create a file outside workspace in a sibling temp dir
            val outsideDir = Files.createTempDirectory("outside")
            val outsideFile = outsideDir.resolve("external.txt")
            Files.writeString(outsideFile, "external content\n")

            try {
                val result = ReadTool.execute(
                    ReadTool.Args(path = outsideFile.toAbsolutePath().toString())
                )
                result shouldContain "external content"
            } finally {
                Files.deleteIfExists(outsideFile)
                Files.deleteIfExists(outsideDir)
            }
        }
    }
}
