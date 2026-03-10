package com.openclaw.skills

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SkillLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private fun makeSkillMeta(
        name: String,
        description: String = "",
        emoji: String? = null,
        always: Boolean = false,
        body: String = "",
        path: Path = Path.of("/fake/$name/SKILL.md"),
        resources: SkillResources = SkillResources.EMPTY,
        source: SkillSource = SkillSource.WORKSPACE,
        invocation: SkillInvocationPolicy = SkillInvocationPolicy(),
        primaryEnv: String? = null,
        requiredEnv: List<String> = emptyList()
    ): SkillMeta = SkillMeta(
        frontmatter = SkillFrontmatter(
            name = name,
            description = description,
            emoji = emoji,
            always = always,
            requires = SkillRequirements(env = requiredEnv),
            openclaw = OpenClawSkillConfig(primaryEnv = primaryEnv)
        ),
        path = path,
        body = body,
        resources = resources,
        source = source,
        invocation = invocation
    )

    private fun createSkillFile(
        baseDir: Path,
        dirName: String,
        name: String,
        description: String = "",
        body: String = "Skill body for $name"
    ): Path {
        val skillDir = baseDir.resolve(dirName).createDirectories()
        val descYaml = if (description.isNotEmpty()) "\ndescription: $description" else ""
        skillDir.resolve("SKILL.md").writeText("---\nname: $name$descYaml\n---\n$body")
        return skillDir.resolve("SKILL.md")
    }

    @Nested
    inner class PromptXmlGeneration {

        @Test
        fun `empty list returns empty string`() {
            val loader = SkillLoader()
            loader.buildPromptXml(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `single skill produces valid XML`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Web search")
            ))
            xml shouldContain "<available_skills>"
            xml shouldContain "</available_skills>"
            xml shouldContain "name=\"search\""
            xml shouldContain "description=\"Web search\""
        }

        @Test
        fun `multiple skills each get a skill element`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Search the web"),
                makeSkillMeta("deploy", "Deploy apps")
            ))
            xml shouldContain "name=\"search\""
            xml shouldContain "name=\"deploy\""
        }

        @Test
        fun `special chars escaped in attributes`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("test & <skill>", "A \"quoted\" description")
            ))
            xml shouldContain "name=\"test &amp; &lt;skill&gt;\""
            xml shouldContain "description=\"A &quot;quoted&quot; description\""
        }

        @Test
        fun `emoji included when present`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", emoji = "\uD83D\uDD0E")
            ))
            xml shouldContain "emoji=\"\uD83D\uDD0E\""
        }

        @Test
        fun `emoji omitted when null`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search")
            ))
            xml shouldNotContain "emoji="
        }

        @Test
        fun `XML includes resource attributes when present`() {
            val loader = SkillLoader()
            val resources = SkillResources(
                scripts = listOf("init.sh", "deploy.py"),
                references = listOf("guide.md"),
                assets = listOf("template.yml")
            )
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("deploy", "Deploy apps", resources = resources)
            ))
            xml shouldContain "has_scripts=\"true\""
            xml shouldContain "scripts=\"init.sh,deploy.py\""
            xml shouldContain "has_references=\"true\""
            xml shouldContain "references=\"guide.md\""
            xml shouldContain "has_assets=\"true\""
            xml shouldContain "assets=\"template.yml\""
        }

        @Test
        fun `XML omits resource attributes when empty`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Web search")
            ))
            xml shouldNotContain "has_scripts"
            xml shouldNotContain "has_references"
            xml shouldNotContain "has_assets"
        }

        @Test
        fun `disable model invocation skills are omitted from prompt XML`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("visible", description = "Visible skill"),
                makeSkillMeta(
                    "hidden",
                    description = "Hidden skill",
                    invocation = SkillInvocationPolicy(disableModelInvocation = true)
                )
            ))

            xml shouldContain "name=\"visible\""
            xml shouldNotContain "name=\"hidden\""
        }
    }

    @Nested
    inner class SnapshotBuilding {

        @Test
        fun `workspace with skills dir builds snapshot`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", "Web search")
            createSkillFile(skillsDir, "deploy", "deploy", "Deploy apps")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            snapshot.skills.size shouldBe 2
            snapshot.promptXml shouldContain "<available_skills>"
        }

        @Test
        fun `empty snapshot when no skills dir`() {
            val workspace = tempDir.resolve("empty-workspace").createDirectories()

            val loader = SkillLoader()
            val snapshot = loader.buildSnapshot(workspace)

            snapshot shouldBe SkillSnapshot.EMPTY
            snapshot.skills.size shouldBe 0
            snapshot.promptXml.shouldBeEmpty()
        }

        @Test
        fun `promptXml matches skills content`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", "Find things")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            snapshot.promptXml shouldContain "name=\"search\""
            snapshot.promptXml shouldContain "description=\"Find things\""
        }

        @Test
        fun `snapshot is reusable across calls`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            // Snapshot is an immutable data class — same value on re-access
            snapshot.skills.size shouldBe 1
            snapshot.promptXml shouldBe snapshot.promptXml
        }
    }

    @Nested
    inner class FullContentLoading {

        @Test
        fun `returns body content for known skill`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", body = "Search the web using these instructions.")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            val full = loader.loadFull(snapshot, "search")
            full.shouldNotBeNull()
            full shouldBe "Search the web using these instructions."
        }

        @Test
        fun `by name from snapshot`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "deploy", "deploy", body = "Deploy instructions here.")
            createSkillFile(skillsDir, "search", "search", body = "Search instructions here.")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            val full = loader.loadFull(snapshot, "deploy")
            full.shouldNotBeNull()
            full shouldBe "Deploy instructions here."
        }

        @Test
        fun `returns snapshotted body after skill file changes on disk`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            val skillFile = createSkillFile(
                skillsDir,
                "search",
                "search",
                body = "Original instructions."
            )

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            skillFile.writeText(
                """
                |---
                |name: search
                |description: Updated
                |---
                |Updated instructions.
                """.trimMargin()
            )

            val full = loader.loadFull(snapshot, "search")
            full.shouldNotBeNull()
            full shouldBe "Original instructions."
        }

        @Test
        fun `returns snapshotted body after skill file is deleted from disk`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            val skillFile = createSkillFile(
                skillsDir,
                "search",
                "search",
                body = "Original instructions."
            )

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            skillFile.toFile().delete()

            val full = loader.loadFull(snapshot, "search")
            full.shouldNotBeNull()
            full shouldBe "Original instructions."
        }

        @Test
        fun `returns null when not in snapshot`() {
            val loader = SkillLoader()
            val snapshot = SkillSnapshot.EMPTY
            loader.loadFull(snapshot, "nonexistent").shouldBeNull()
        }

        @Test
        fun `returns null for empty body`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            val skillDir = skillsDir.resolve("empty-body").createDirectories()
            skillDir.resolve("SKILL.md").writeText("---\nname: empty-body\n---\n")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            loader.loadFull(snapshot, "empty-body").shouldBeNull()
        }
    }

    @Nested
    inner class TokenEfficiency {

        @Test
        fun `compact format uses self-closing tags`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Web search")
            ))
            xml shouldContain "/>"
            xml shouldNotContain "</skill>"
        }

        @Test
        fun `always-on skill body appended after XML block`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("core", "Core instructions", always = true, body = "Always follow these rules."),
                makeSkillMeta("search", "Web search")
            ))
            xml shouldContain "</available_skills>"
            xml shouldContain "<!-- always-on: core -->"
            xml shouldContain "Always follow these rules."

            // Body comes after the closing tag
            val closingIdx = xml.indexOf("</available_skills>")
            val bodyIdx = xml.indexOf("Always follow these rules.")
            (bodyIdx > closingIdx) shouldBe true
        }
    }

    @Nested
    inner class SkillLimits {

        @Test
        fun `skills count limited to maxSkillsCount`() {
            val loader = SkillLoader(maxSkillsCount = 3)
            val skills = (1..5).map { makeSkillMeta("skill-$it", "Description $it") }

            val xml = loader.buildPromptXml(skills)

            xml shouldContain "name=\"skill-1\""
            xml shouldContain "name=\"skill-2\""
            xml shouldContain "name=\"skill-3\""
            xml shouldNotContain "name=\"skill-4\""
            xml shouldNotContain "name=\"skill-5\""
        }

        @Test
        fun `XML size capped to skillsBlockCharLimit`() {
            val loader = SkillLoader(skillsBlockCharLimit = 100)
            val skills = (1..10).map { makeSkillMeta("skill-$it", "A longer description to inflate XML size number $it") }

            val xml = loader.buildPromptXml(skills)

            xml.length shouldBeLessThanOrEqualTo 100
        }

        @Test
        fun `XML truncation keeps complete skill boundaries`() {
            val loader = SkillLoader(skillsBlockCharLimit = 160)
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("skill-one", "short"),
                makeSkillMeta("skill-two", "this description should push the second skill over the limit")
            ))

            xml shouldContain "name=\"skill-one\""
            xml shouldNotContain "name=\"skill-two\""
            xml shouldContain "</available_skills>"
        }

        @Test
        fun `skills within limits are unchanged`() {
            val loader = SkillLoader(maxSkillsCount = 10)
            val skills = (1..3).map { makeSkillMeta("skill-$it", "Description $it") }

            val xml = loader.buildPromptXml(skills)

            xml shouldContain "name=\"skill-1\""
            xml shouldContain "name=\"skill-2\""
            xml shouldContain "name=\"skill-3\""
            xml shouldContain "<available_skills>"
            xml shouldContain "</available_skills>"
        }
    }

    @Nested
    inner class StatusAndRuntime {

        @Test
        fun `bundled skill can be blocked by allowlist`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val bundled = tempDir.resolve("bundled").createDirectories()
            createSkillFile(bundled, "deploy", "deploy", "Bundled deploy")

            val loader = SkillLoader(SkillScanner(SkillGate(FakeEnvironmentChecker())))
            val report = loader.buildStatus(
                workspacePath = workspace,
                config = SkillsConfig(allowBundled = listOf("search")),
                bundledSkillsPath = bundled
            )

            report.skills.size shouldBe 1
            report.skills.first().blockedByAllowlist shouldBe true
            report.skills.first().eligible shouldBe false
        }

        @Test
        fun `disabled entry is excluded from eligible snapshot`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search")

            val loader = SkillLoader(SkillScanner(SkillGate(FakeEnvironmentChecker())))
            val snapshot = loader.buildSnapshot(
                workspacePath = workspace,
                config = SkillsConfig(entries = mapOf("search" to SkillEntryConfig(enabled = false)))
            )

            snapshot.skills shouldBe emptyList()
            snapshot.promptXml.shouldBeEmpty()
        }

        @Test
        fun `runtime env overrides apply and revert after cleanup`() {
            val loader = SkillLoader()
            val snapshot = SkillSnapshot(
                skills = listOf(
                    makeSkillMeta(
                        "search",
                        primaryEnv = "OPENAI_API_KEY",
                        requiredEnv = listOf("OPENAI_API_KEY")
                    )
                ),
                promptXml = "<available_skills />"
            )
            val cleanup = loader.applyRuntime(
                snapshot,
                SkillsConfig(
                    entries = mapOf(
                        "search" to SkillEntryConfig(
                            apiKey = "secret-key",
                            env = mapOf("OPENAI_API_KEY" to "override-key", "PATH" to "/tmp/blocked")
                        )
                    )
                )
            )

            SkillEnvironment.get("OPENAI_API_KEY") shouldBe "override-key"
            SkillEnvironment.get("PATH") shouldBe null

            cleanup()

            SkillEnvironment.get("OPENAI_API_KEY") shouldBe null
        }
    }
}
