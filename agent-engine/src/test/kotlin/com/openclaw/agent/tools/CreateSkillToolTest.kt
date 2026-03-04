package com.openclaw.agent.tools

import com.openclaw.skills.SkillLoader
import com.openclaw.skills.SkillParser
import com.openclaw.skills.SkillResources
import com.openclaw.skills.SkillScanner
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class CreateSkillToolTest {

    @TempDir
    lateinit var tempDir: Path

    private fun tool(workspace: Path = tempDir): CreateSkillTool =
        CreateSkillTool { workspace }

    private fun minimalArgs(
        name: String = "test-skill",
        description: String = "A test skill",
        body: String = "Do the thing."
    ) = CreateSkillTool.Args(
        name = name,
        description = description,
        body = body
    )

    @Nested
    inner class ValidCreation {

        @Test
        fun `creates SKILL_md with correct YAML frontmatter and body`() = runTest {
            val result = tool().execute(minimalArgs())

            result shouldStartWith "Created skill"
            val skillFile = tempDir.resolve("skills/test-skill/SKILL.md")
            skillFile.exists() shouldBe true

            val content = skillFile.readText()
            content shouldContain "name: test-skill"
            content shouldContain "description: A test skill"
            content shouldContain "Do the thing."
        }

        @Test
        fun `creates directory structure`() = runTest {
            tool().execute(minimalArgs(name = "my-new-skill"))

            tempDir.resolve("skills/my-new-skill").exists() shouldBe true
            tempDir.resolve("skills/my-new-skill/SKILL.md").exists() shouldBe true
        }

        @Test
        fun `includes emoji and requirements when provided`() = runTest {
            val args = CreateSkillTool.Args(
                name = "docker-deploy",
                description = "Deploy with Docker",
                body = "Run docker compose up.",
                emoji = "\uD83D\uDC33",
                requiredBins = listOf("docker", "kubectl"),
                requiredEnv = listOf("DOCKER_HOST")
            )
            tool().execute(args)

            val content = tempDir.resolve("skills/docker-deploy/SKILL.md").readText()
            content shouldContain "emoji: \"\uD83D\uDC33\""
            content shouldContain "bins: [docker, kubectl]"
            content shouldContain "env: [DOCKER_HOST]"
        }

        @Test
        fun `body content preserved verbatim`() = runTest {
            val body = """
                # Step 1
                Do this first.

                # Step 2
                Then do this.

                ```bash
                echo "hello"
                ```
            """.trimIndent()
            tool().execute(minimalArgs(body = body))

            val content = tempDir.resolve("skills/test-skill/SKILL.md").readText()
            content shouldContain body
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `blank name returns error`() = runTest {
            val result = tool().execute(minimalArgs(name = "   "))
            result shouldStartWith "Error:"
            result shouldContain "blank"
        }

        @Test
        fun `invalid characters return error`() = runTest {
            val result = tool().execute(minimalArgs(name = "My Skill"))
            result shouldStartWith "Error:"
            result shouldContain "kebab-case"
        }

        @Test
        fun `existing skill returns error`() = runTest {
            tool().execute(minimalArgs(name = "exists"))
            val result = tool().execute(minimalArgs(name = "exists"))
            result shouldStartWith "Error:"
            result shouldContain "already exists"
        }
    }

    @Nested
    inner class ResourceCreation {

        @Test
        fun `creates scripts directory with files`() = runTest {
            val args = CreateSkillTool.Args(
                name = "scripted",
                description = "A scripted skill",
                body = "Run the script.",
                scripts = listOf(
                    CreateSkillTool.SkillFile("init.sh", "#!/bin/bash\necho hello"),
                    CreateSkillTool.SkillFile("deploy.py", "print('deploying')")
                )
            )
            val result = tool().execute(args)
            result shouldContain "2 script(s)"

            val scriptsDir = tempDir.resolve("skills/scripted/scripts")
            scriptsDir.exists() shouldBe true
            scriptsDir.resolve("init.sh").readText() shouldBe "#!/bin/bash\necho hello"
            scriptsDir.resolve("deploy.py").readText() shouldBe "print('deploying')"
        }

        @Test
        @DisabledOnOs(OS.WINDOWS)
        fun `scripts are executable on unix`() = runTest {
            val args = CreateSkillTool.Args(
                name = "exec-test",
                description = "Test executable",
                body = "Run it.",
                scripts = listOf(CreateSkillTool.SkillFile("run.sh", "#!/bin/bash"))
            )
            tool().execute(args)

            val perms = Files.getPosixFilePermissions(tempDir.resolve("skills/exec-test/scripts/run.sh"))
            perms.toString() shouldContain "OWNER_EXECUTE"
        }

        @Test
        fun `creates references directory with files`() = runTest {
            val args = CreateSkillTool.Args(
                name = "with-refs",
                description = "Has references",
                body = "Read the guide.",
                references = listOf(CreateSkillTool.SkillFile("guide.md", "# Guide\nDo this."))
            )
            val result = tool().execute(args)
            result shouldContain "1 reference(s)"

            tempDir.resolve("skills/with-refs/references/guide.md").readText() shouldBe "# Guide\nDo this."
        }

        @Test
        fun `creates assets directory with files`() = runTest {
            val args = CreateSkillTool.Args(
                name = "with-assets",
                description = "Has assets",
                body = "Use the template.",
                assets = listOf(CreateSkillTool.SkillFile("template.yml", "key: value"))
            )
            val result = tool().execute(args)
            result shouldContain "1 asset(s)"

            tempDir.resolve("skills/with-assets/assets/template.yml").readText() shouldBe "key: value"
        }

        @Test
        fun `creates all resource types together`() = runTest {
            val args = CreateSkillTool.Args(
                name = "full-skill",
                description = "Full skill",
                body = "Do everything.",
                scripts = listOf(CreateSkillTool.SkillFile("run.sh", "#!/bin/bash")),
                references = listOf(CreateSkillTool.SkillFile("guide.md", "# Guide")),
                assets = listOf(CreateSkillTool.SkillFile("config.yml", "key: val"))
            )
            val result = tool().execute(args)
            result shouldContain "1 script(s)"
            result shouldContain "1 reference(s)"
            result shouldContain "1 asset(s)"

            tempDir.resolve("skills/full-skill/scripts/run.sh").exists() shouldBe true
            tempDir.resolve("skills/full-skill/references/guide.md").exists() shouldBe true
            tempDir.resolve("skills/full-skill/assets/config.yml").exists() shouldBe true
        }

        @Test
        fun `skips empty resource lists`() = runTest {
            tool().execute(minimalArgs(name = "no-resources"))

            tempDir.resolve("skills/no-resources/scripts").exists() shouldBe false
            tempDir.resolve("skills/no-resources/references").exists() shouldBe false
            tempDir.resolve("skills/no-resources/assets").exists() shouldBe false
        }

        @Test
        fun `rejects path traversal in file names`() = runTest {
            val args = CreateSkillTool.Args(
                name = "evil",
                description = "Bad skill",
                body = "Bad.",
                scripts = listOf(CreateSkillTool.SkillFile("../evil.sh", "rm -rf /"))
            )
            val result = tool().execute(args)
            result shouldStartWith "Error:"
            result shouldContain ".."
        }

        @Test
        fun `rejects path separators in file names`() = runTest {
            val args = CreateSkillTool.Args(
                name = "evil2",
                description = "Bad skill",
                body = "Bad.",
                references = listOf(CreateSkillTool.SkillFile("sub/file.md", "content"))
            )
            val result = tool().execute(args)
            result shouldStartWith "Error:"
            result shouldContain "path separator"
        }
    }

    @Nested
    inner class ResourceIntegration {

        @Test
        fun `created skill with scripts discoverable by SkillScanner with resources`() = runTest {
            val args = CreateSkillTool.Args(
                name = "discoverable",
                description = "Has scripts",
                body = "Run scripts.",
                scripts = listOf(CreateSkillTool.SkillFile("run.sh", "#!/bin/bash"))
            )
            tool().execute(args)

            val scanner = SkillScanner()
            val results = scanner.scan(listOf(tempDir.resolve("skills")))
            results.size shouldBe 1
            results[0].resources.hasScripts shouldBe true
            results[0].resources.scripts shouldBe listOf("run.sh")
        }

        @Test
        fun `prompt XML includes resource attributes`() = runTest {
            val args = CreateSkillTool.Args(
                name = "xml-test",
                description = "XML test",
                body = "Test.",
                scripts = listOf(CreateSkillTool.SkillFile("run.sh", "#!/bin/bash")),
                references = listOf(CreateSkillTool.SkillFile("ref.md", "# Ref"))
            )
            tool().execute(args)

            val scanner = SkillScanner()
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(tempDir)
            snapshot.promptXml shouldContain "has_scripts=\"true\""
            snapshot.promptXml shouldContain "scripts=\"run.sh\""
            snapshot.promptXml shouldContain "has_references=\"true\""
            snapshot.promptXml shouldContain "references=\"ref.md\""
        }
    }

    @Nested
    inner class Integration {

        @Test
        fun `created skill is parseable by SkillParser`() = runTest {
            tool().execute(minimalArgs(name = "parseable"))

            val skillFile = tempDir.resolve("skills/parseable/SKILL.md")
            val parsed = SkillParser.parse(skillFile)
            parsed shouldBe parsed // non-null
            parsed!!.name shouldBe "parseable"
            parsed.description shouldBe "A test skill"
            parsed.body shouldBe "Do the thing."
        }

        @Test
        fun `created skill discoverable by SkillScanner`() = runTest {
            tool().execute(minimalArgs(name = "scannable"))

            // Default scanner uses real EnvironmentChecker; skill has no requirements so it passes
            val scanner = SkillScanner()
            val results = scanner.scan(listOf(tempDir.resolve("skills")))

            results.size shouldBe 1
            results[0].name shouldBe "scannable"
        }

        @Test
        fun `returned path matches expected location`() = runTest {
            val result = tool().execute(minimalArgs(name = "located"))
            val expectedPath = tempDir.resolve("skills/located/SKILL.md")
            result shouldContain expectedPath.toString()
        }
    }
}
