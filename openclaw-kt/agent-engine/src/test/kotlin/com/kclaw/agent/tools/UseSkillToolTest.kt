package com.kclaw.agent.tools

import com.kclaw.skills.SkillLoader
import com.kclaw.skills.SkillsConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UseSkillToolTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeSkill(
        workspace: Path,
        name: String,
        description: String = "A test skill",
        body: String = "Follow these instructions.",
        always: Boolean = false
    ) {
        val skillDir = workspace.resolve("skills").resolve(name)
        Files.createDirectories(skillDir)
        val content = buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: $description")
            if (always) appendLine("always: true")
            appendLine("---")
            append(body)
        }
        Files.writeString(skillDir.resolve("SKILL.md"), content)
    }

    private fun buildSnapshotAndTool(workspace: Path): UseSkillTool {
        val loader = SkillLoader()
        // Build snapshot to populate the cache
        loader.buildSnapshot(workspacePath = workspace, config = SkillsConfig())
        return UseSkillTool(loader) { workspace }
    }

    @Nested
    inner class SkillFound {

        @Test
        fun `returns skill body when skill exists`() = runTest {
            writeSkill(tempDir, "greeter", body = "Always respond with: Habari")
            val tool = buildSnapshotAndTool(tempDir)

            val result = tool.execute(UseSkillTool.Args(name = "greeter"))

            result shouldContain "Habari"
        }

        @Test
        fun `returns full body with markdown`() = runTest {
            val body = """
                # Greeting Skill

                ## Rules
                - Always greet in Swahili
                - Say: Habari
            """.trimIndent()
            writeSkill(tempDir, "swahili-greeter", body = body)
            val tool = buildSnapshotAndTool(tempDir)

            val result = tool.execute(UseSkillTool.Args(name = "swahili-greeter"))

            result shouldContain "# Greeting Skill"
            result shouldContain "Always greet in Swahili"
            result shouldContain "Habari"
        }

        @Test
        fun `trims whitespace from skill name`() = runTest {
            writeSkill(tempDir, "my-skill", body = "Do the thing.")
            val tool = buildSnapshotAndTool(tempDir)

            val result = tool.execute(UseSkillTool.Args(name = "  my-skill  "))

            result shouldContain "Do the thing."
        }
    }

    @Nested
    inner class SkillNotFound {

        @Test
        fun `returns error for nonexistent skill`() = runTest {
            writeSkill(tempDir, "existing-skill")
            val tool = buildSnapshotAndTool(tempDir)

            val result = tool.execute(UseSkillTool.Args(name = "nonexistent"))

            result shouldStartWith "Error:"
            result shouldContain "nonexistent"
        }

        @Test
        fun `returns error for blank name`() = runTest {
            val loader = SkillLoader()
            val tool = UseSkillTool(loader) { tempDir }

            val result = tool.execute(UseSkillTool.Args(name = "   "))

            result shouldStartWith "Error:"
            result shouldContain "blank"
        }

        @Test
        fun `returns error when no snapshot cached`() = runTest {
            val loader = SkillLoader()
            val tool = UseSkillTool(loader) { tempDir }

            val result = tool.execute(UseSkillTool.Args(name = "some-skill"))

            result shouldStartWith "Error:"
            result shouldContain "some-skill"
        }
    }

    @Nested
    inner class MultipleSkills {

        @Test
        fun `loads correct skill from multiple`() = runTest {
            writeSkill(tempDir, "skill-a", body = "Instructions for A")
            writeSkill(tempDir, "skill-b", body = "Instructions for B")
            writeSkill(tempDir, "skill-c", body = "Instructions for C")
            val tool = buildSnapshotAndTool(tempDir)

            val resultB = tool.execute(UseSkillTool.Args(name = "skill-b"))
            resultB shouldContain "Instructions for B"

            val resultA = tool.execute(UseSkillTool.Args(name = "skill-a"))
            resultA shouldContain "Instructions for A"
        }
    }
}