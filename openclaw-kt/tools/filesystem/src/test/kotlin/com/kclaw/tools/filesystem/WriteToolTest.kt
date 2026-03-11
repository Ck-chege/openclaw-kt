package com.kclaw.tools.filesystem

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WriteToolTest {

    @TempDir
    lateinit var workspace: Path

    @BeforeEach
    fun setUp() {
        WriteTool.workspacePath = workspace
    }

    @Nested
    inner class BasicWriting {

        @Test
        fun `creates new file with content`() = runTest {
            val result = WriteTool.execute(
                WriteTool.Args(path = "new.txt", content = "hello world")
            )

            result shouldContain "Wrote"
            result shouldContain "bytes to new.txt"
            val written = workspace.resolve("new.txt").toFile().readText()
            written shouldBe "hello world"
        }

        @Test
        fun `overwrites existing file`() = runTest {
            val file = workspace.resolve("existing.txt")
            Files.writeString(file, "old content")

            val result = WriteTool.execute(
                WriteTool.Args(path = "existing.txt", content = "new content")
            )

            result shouldContain "Wrote"
            val written = file.toFile().readText()
            written shouldBe "new content"
        }

        @Test
        fun `creates parent directories`() = runTest {
            val result = WriteTool.execute(
                WriteTool.Args(path = "deep/nested/dir/file.txt", content = "nested content")
            )

            result shouldContain "Wrote"
            val written = workspace.resolve("deep/nested/dir/file.txt").toFile().readText()
            written shouldBe "nested content"
        }
    }

    @Nested
    inner class BoundaryEnforcement {

        @Test
        fun `blocks write outside workspace`() = runTest {
            val result = WriteTool.execute(
                WriteTool.Args(path = "/tmp/evil.txt", content = "hacked")
            )

            result shouldContain "Error:"
        }

        @Test
        fun `blocks path traversal escape`() = runTest {
            val result = WriteTool.execute(
                WriteTool.Args(path = "../escape.txt", content = "escaped")
            )

            result shouldContain "Error:"
        }
    }
}
