package com.openclaw.agent.workspace

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class WorkspaceLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class ReadExistingFiles {

        @Test
        fun `reads existing SOUL md content correctly`() {
            tempDir.resolve("SOUL.md").writeText("You are a helpful assistant.")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe "You are a helpful assistant."
        }

        @Test
        fun `reads existing AGENTS md content correctly`() {
            tempDir.resolve("AGENTS.md").writeText("Agent configuration here")
            val loader = WorkspaceLoader(tempDir)
            loader.readAgentsMd() shouldBe "Agent configuration here"
        }

        @Test
        fun `reads existing USER md content correctly`() {
            tempDir.resolve("USER.md").writeText("User preferences")
            val loader = WorkspaceLoader(tempDir)
            loader.readUserMd() shouldBe "User preferences"
        }

        @Test
        fun `reads existing TOOLS md content correctly`() {
            tempDir.resolve("TOOLS.md").writeText("Available tools")
            val loader = WorkspaceLoader(tempDir)
            loader.readToolsMd() shouldBe "Available tools"
        }

        @Test
        fun `reads existing MEMORY md content correctly`() {
            tempDir.resolve("MEMORY.md").writeText("Session memory")
            val loader = WorkspaceLoader(tempDir)
            loader.readMemoryMd() shouldBe "Session memory"
        }
    }

    @Nested
    inner class MissingFiles {

        @Test
        fun `missing SOUL md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe ""
        }

        @Test
        fun `missing AGENTS md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readAgentsMd() shouldBe ""
        }

        @Test
        fun `missing USER md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readUserMd() shouldBe ""
        }

        @Test
        fun `missing TOOLS md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readToolsMd() shouldBe ""
        }

        @Test
        fun `missing MEMORY md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readMemoryMd() shouldBe ""
        }
    }

    @Nested
    inner class WhitespaceTrimming {

        @Test
        fun `trims leading whitespace from content`() {
            tempDir.resolve("SOUL.md").writeText("   leading spaces")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe "leading spaces"
        }

        @Test
        fun `trims trailing whitespace from content`() {
            tempDir.resolve("SOUL.md").writeText("trailing spaces   \n\n")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe "trailing spaces"
        }

        @Test
        fun `trims both leading and trailing whitespace`() {
            tempDir.resolve("SOUL.md").writeText("\n  content here  \n")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe "content here"
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `non-existent workspace path returns empty for all methods`() {
            val nonExistent = tempDir.resolve("does-not-exist")
            val loader = WorkspaceLoader(nonExistent)
            loader.readSoulMd() shouldBe ""
            loader.readAgentsMd() shouldBe ""
            loader.readUserMd() shouldBe ""
            loader.readToolsMd() shouldBe ""
            loader.readMemoryMd() shouldBe ""
        }

        @Test
        fun `empty file returns empty string`() {
            tempDir.resolve("SOUL.md").writeText("")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe ""
        }

        @Test
        fun `file with only whitespace returns empty string`() {
            tempDir.resolve("SOUL.md").writeText("   \n\t  \n  ")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe ""
        }

        @Test
        fun `multiline content preserves internal newlines`() {
            tempDir.resolve("SOUL.md").writeText("line 1\nline 2\nline 3")
            val loader = WorkspaceLoader(tempDir)
            val content = loader.readSoulMd()
            content shouldContain "line 1\nline 2\nline 3"
        }
    }

    @Nested
    inner class ExtensionFunctions {

        @Test
        fun `Path readSoulMd extension works`() {
            tempDir.resolve("SOUL.md").writeText("Extension test")
            tempDir.readSoulMd() shouldBe "Extension test"
        }

        @Test
        fun `Path readAgentsMd extension works`() {
            tempDir.resolve("AGENTS.md").writeText("Agents ext test")
            tempDir.readAgentsMd() shouldBe "Agents ext test"
        }

        @Test
        fun `Path readUserMd extension works`() {
            tempDir.resolve("USER.md").writeText("User ext test")
            tempDir.readUserMd() shouldBe "User ext test"
        }

        @Test
        fun `Path readToolsMd extension works`() {
            tempDir.resolve("TOOLS.md").writeText("Tools ext test")
            tempDir.readToolsMd() shouldBe "Tools ext test"
        }

        @Test
        fun `Path readMemoryMd extension works`() {
            tempDir.resolve("MEMORY.md").writeText("Memory ext test")
            tempDir.readMemoryMd() shouldBe "Memory ext test"
        }
    }

    @Nested
    inner class NewReadMethods {

        @Test
        fun `reads existing IDENTITY md content correctly`() {
            tempDir.resolve("IDENTITY.md").writeText("Identity content")
            val loader = WorkspaceLoader(tempDir)
            loader.readIdentityMd() shouldBe "Identity content"
        }

        @Test
        fun `reads existing BOOTSTRAP md content correctly`() {
            tempDir.resolve("BOOTSTRAP.md").writeText("Bootstrap content")
            val loader = WorkspaceLoader(tempDir)
            loader.readBootstrapMd() shouldBe "Bootstrap content"
        }

        @Test
        fun `reads existing HEARTBEAT md content correctly`() {
            tempDir.resolve("HEARTBEAT.md").writeText("Heartbeat content")
            val loader = WorkspaceLoader(tempDir)
            loader.readHeartbeatMd() shouldBe "Heartbeat content"
        }

        @Test
        fun `missing IDENTITY md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readIdentityMd() shouldBe ""
        }

        @Test
        fun `missing BOOTSTRAP md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readBootstrapMd() shouldBe ""
        }

        @Test
        fun `missing HEARTBEAT md returns empty string`() {
            val loader = WorkspaceLoader(tempDir)
            loader.readHeartbeatMd() shouldBe ""
        }
    }

    @Nested
    inner class FileSizeGuard {

        @Test
        fun `file under 2MB limit is read normally`() {
            tempDir.resolve("SOUL.md").writeText("small content")
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe "small content"
        }

        @Test
        fun `file at exactly 2MB limit is read normally`() {
            val exactSize = 2 * 1024 * 1024
            val content = "A".repeat(exactSize)
            tempDir.resolve("SOUL.md").writeText(content)
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe content
        }

        @Test
        fun `file exceeding 2MB limit returns empty string`() {
            val overSize = 2 * 1024 * 1024 + 1
            val content = ByteArray(overSize) { 65 } // 'A' bytes
            tempDir.resolve("SOUL.md").writeBytes(content)
            val loader = WorkspaceLoader(tempDir)
            loader.readSoulMd() shouldBe ""
        }
    }

    @Nested
    inner class NewExtensionFunctions {

        @Test
        fun `Path readIdentityMd extension works`() {
            tempDir.resolve("IDENTITY.md").writeText("Identity ext test")
            tempDir.readIdentityMd() shouldBe "Identity ext test"
        }

        @Test
        fun `Path readBootstrapMd extension works`() {
            tempDir.resolve("BOOTSTRAP.md").writeText("Bootstrap ext test")
            tempDir.readBootstrapMd() shouldBe "Bootstrap ext test"
        }

        @Test
        fun `Path readHeartbeatMd extension works`() {
            tempDir.resolve("HEARTBEAT.md").writeText("Heartbeat ext test")
            tempDir.readHeartbeatMd() shouldBe "Heartbeat ext test"
        }
    }
}
