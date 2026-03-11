package com.kclaw.tools

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceBoundaryTest {

    @TempDir
    lateinit var workspace: Path

    @Nested
    inner class Resolve {

        @Test
        fun `relative path resolved within workspace`() {
            val file = Files.createFile(workspace.resolve("test.txt"))
            val resolved = WorkspaceBoundary.resolve("test.txt", workspace)
            resolved shouldBe workspace.resolve("test.txt").normalize()
        }

        @Test
        fun `absolute path within workspace allowed`() {
            val file = Files.createFile(workspace.resolve("inner.txt"))
            val resolved = WorkspaceBoundary.resolve(file.toString(), workspace)
            resolved shouldBe file.normalize()
        }

        @Test
        fun `absolute path outside workspace rejected`() {
            val ex = shouldThrow<WorkspaceBoundary.WorkspaceBoundaryViolation> {
                WorkspaceBoundary.resolve("/etc/passwd", workspace)
            }
            ex.message shouldContain "outside workspace"
        }

        @Test
        fun `dot-dot traversal caught`() {
            shouldThrow<WorkspaceBoundary.WorkspaceBoundaryViolation> {
                WorkspaceBoundary.resolve("../../../etc/passwd", workspace)
            }
        }

        @Test
        fun `normalized path with redundant separators works`() {
            Files.createFile(workspace.resolve("data.txt"))
            val resolved = WorkspaceBoundary.resolve("./data.txt", workspace)
            resolved shouldBe workspace.resolve("data.txt").normalize()
        }
    }

    @Nested
    inner class IsWithin {

        @Test
        fun `path inside workspace returns true`() {
            WorkspaceBoundary.isWithin(workspace.resolve("file.txt"), workspace) shouldBe true
        }

        @Test
        fun `path outside workspace returns false`() {
            WorkspaceBoundary.isWithin(Path.of("/tmp/other"), workspace) shouldBe false
        }

        @Test
        fun `workspace root itself returns true`() {
            WorkspaceBoundary.isWithin(workspace, workspace) shouldBe true
        }
    }

    @Nested
    inner class SymlinkEscape {

        @Test
        fun `symlink pointing outside workspace is blocked`() {
            val outsideDir = Files.createTempDirectory("outside")
            val outsideFile = Files.createFile(outsideDir.resolve("secret.txt"))
            val symlink = workspace.resolve("sneaky-link")
            Files.createSymbolicLink(symlink, outsideFile)

            shouldThrow<WorkspaceBoundary.WorkspaceBoundaryViolation> {
                WorkspaceBoundary.resolve("sneaky-link", workspace)
            }

            // cleanup
            Files.deleteIfExists(outsideFile)
            Files.deleteIfExists(outsideDir)
        }
    }
}
