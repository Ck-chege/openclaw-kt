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

class EditToolTest {

    @TempDir
    lateinit var workspace: Path

    @BeforeEach
    fun setUp() {
        EditTool.workspacePath = workspace
    }

    @Nested
    inner class BasicEditing {

        @Test
        fun `replaces first occurrence of old_string`() = runTest {
            val file = workspace.resolve("test.txt")
            Files.writeString(file, "hello world, hello again")

            val result = EditTool.execute(
                EditTool.Args(path = "test.txt", old_string = "hello", new_string = "goodbye")
            )

            result shouldContain "Edited"
            val content = file.toFile().readText()
            content shouldBe "goodbye world, hello again"
        }

        @Test
        fun `handles multiline replacement`() = runTest {
            val file = workspace.resolve("multi.txt")
            Files.writeString(file, "line1\nline2\nline3\n")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "multi.txt",
                    old_string = "line1\nline2",
                    new_string = "replaced1\nreplaced2\ninserted"
                )
            )

            result shouldContain "Edited"
            val content = file.toFile().readText()
            content shouldBe "replaced1\nreplaced2\ninserted\nline3\n"
        }

        @Test
        fun `treats special regex characters as literal text`() = runTest {
            val file = workspace.resolve("regex.txt")
            Files.writeString(file, "foo.bar (baz) [qux]")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "regex.txt",
                    old_string = "foo.bar (baz)",
                    new_string = "replaced"
                )
            )

            result shouldContain "Edited"
            val content = file.toFile().readText()
            content shouldBe "replaced [qux]"
        }

        @Test
        fun `same string replacement succeeds as no-op`() = runTest {
            val file = workspace.resolve("noop.txt")
            Files.writeString(file, "keep this text")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "noop.txt",
                    old_string = "keep",
                    new_string = "keep"
                )
            )

            result shouldContain "Edited"
            val content = file.toFile().readText()
            content shouldBe "keep this text"
        }

        @Test
        fun `empty new_string deletes the matched text`() = runTest {
            val file = workspace.resolve("delete.txt")
            Files.writeString(file, "remove this part please")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "delete.txt",
                    old_string = "this part ",
                    new_string = ""
                )
            )

            result shouldContain "Edited"
            val content = file.toFile().readText()
            content shouldBe "remove please"
        }
    }

    @Nested
    inner class ErrorCases {

        @Test
        fun `returns error when old_string not found`() = runTest {
            val file = workspace.resolve("miss.txt")
            Files.writeString(file, "actual content")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "miss.txt",
                    old_string = "nonexistent",
                    new_string = "replacement"
                )
            )

            result shouldContain "Error: old_string not found"
        }

        @Test
        fun `returns error for nonexistent file`() = runTest {
            val result = EditTool.execute(
                EditTool.Args(
                    path = "ghost.txt",
                    old_string = "anything",
                    new_string = "something"
                )
            )

            result shouldContain "Error: File not found"
        }

        @Test
        fun `returns error when old_string is empty`() = runTest {
            val file = workspace.resolve("empty.txt")
            Files.writeString(file, "some content")

            val result = EditTool.execute(
                EditTool.Args(
                    path = "empty.txt",
                    old_string = "",
                    new_string = "something"
                )
            )

            result shouldContain "Error: old_string cannot be empty"
        }
    }

    @Nested
    inner class BoundaryEnforcement {

        @Test
        fun `blocks edit outside workspace`() = runTest {
            val result = EditTool.execute(
                EditTool.Args(
                    path = "/etc/hosts",
                    old_string = "localhost",
                    new_string = "evil"
                )
            )

            result shouldContain "Error:"
        }
    }
}
