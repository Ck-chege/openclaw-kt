package com.kclaw.skills

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
            kclaw = KClawSkillConfig(primaryEnv = primaryEnv)
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
            xml shouldContain "<name>search</name>"
            xml shouldContain "<description>Web search</description>"
        }

        @Test
        fun `multiple skills each get a skill element`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Search the web"),
                makeSkillMeta("deploy", "Deploy apps")
            ))
            xml shouldContain "<name>search</name>"
            xml shouldContain "<name>deploy</name>"
        }

        @Test
        fun `special chars escaped in element content`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("test & <skill>", "A <quoted> description")
            ))
            xml shouldContain "<name>test &amp; &lt;skill&gt;</name>"
            xml shouldContain "<description>A &lt;quoted&gt; description</description>"
        }

        @Test
        fun `emoji omitted — not a separate element in TS-compatible format`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", emoji = "\uD83D\uDD0E")
            ))
            // emoji is not exposed as a dedicated XML element in the TS-compatible format
            xml shouldNotContain "emoji="
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
        fun `XML omits resource metadata — not in TS-compatible format`() {
            val loader = SkillLoader()
            val resources = SkillResources(
                scripts = listOf("scripts/init.sh", "scripts/deploy.py"),
                references = listOf("references/guide.md"),
                assets = listOf("assets/template.yml")
            )
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("deploy", "Deploy apps", resources = resources)
            ))
            // TS-compatible format uses <name>, <description>, <location> only
            xml shouldNotContain "has_scripts"
            xml shouldNotContain "has_references"
            xml shouldNotContain "has_assets"
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

            xml shouldContain "<name>visible</name>"
            xml shouldNotContain "<name>hidden</name>"
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

            snapshot.promptXml shouldContain "<name>search</name>"
            snapshot.promptXml shouldContain "<description>Find things</description>"
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

        @Test
        fun `new skill on disk is picked up without explicit refresh`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", "Web search")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            // fingerprintCheckIntervalMs=0 → fingerprint safety-net runs every call
            // (simulates watcher lag: no refresh() called, but disk changed)
            val config = SkillsConfig(load = SkillsLoadConfig(fingerprintCheckIntervalMs = 0L))

            val snapshot1 = loader.buildSnapshot(workspace, config)
            snapshot1.skills.size shouldBe 1

            // Simulate a skill being installed (e.g., by exec tool) after snapshot was built
            Thread.sleep(50) // ensure mtime granularity
            createSkillFile(skillsDir, "deploy", "deploy", "Deploy apps")

            val snapshot2 = loader.buildSnapshot(workspace, config)
            snapshot2.skills.size shouldBe 2
            snapshot2.promptXml shouldContain "<name>deploy</name>"
        }

        @Test
        fun `modified SKILL_md is detected without explicit refresh`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            val skillFile = createSkillFile(skillsDir, "search", "search", "Find things", body = "Original body")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            // fingerprintCheckIntervalMs=0 → fingerprint safety-net runs every call
            val config = SkillsConfig(load = SkillsLoadConfig(fingerprintCheckIntervalMs = 0L))

            val snapshot1 = loader.buildSnapshot(workspace, config)
            snapshot1.promptXml shouldContain "<description>Find things</description>"

            Thread.sleep(50)
            skillFile.writeText("---\nname: search\ndescription: Updated search\n---\nNew body")

            val snapshot2 = loader.buildSnapshot(workspace, config)
            snapshot2.promptXml shouldContain "<description>Updated search</description>"
        }

        @Test
        fun `cache is used when no filesystem changes`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)

            val snapshot1 = loader.buildSnapshot(workspace)
            val snapshot2 = loader.buildSnapshot(workspace)

            // Same object reference — cache hit, no rescan
            (snapshot1 === snapshot2) shouldBe true
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
    inner class CachedFullLoading {

        @Test
        fun `returns body from cached snapshot`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", body = "Search instructions.")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)

            // Build snapshot to populate cache
            loader.buildSnapshot(workspace)

            val body = loader.loadFullFromCache(workspace, "search")
            body.shouldNotBeNull()
            body shouldBe "Search instructions."
        }

        @Test
        fun `returns null when no snapshot is cached`() {
            val loader = SkillLoader()
            loader.loadFullFromCache(tempDir, "anything").shouldBeNull()
        }

        @Test
        fun `returns null for unknown skill name`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "search", "search", body = "Search body")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            loader.buildSnapshot(workspace)

            loader.loadFullFromCache(workspace, "nonexistent").shouldBeNull()
        }

        @Test
        fun `returns correct body with multiple cached skills`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "skill-a", "skill-a", body = "Instructions A")
            createSkillFile(skillsDir, "skill-b", "skill-b", body = "Instructions B")

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            loader.buildSnapshot(workspace)

            loader.loadFullFromCache(workspace, "skill-a") shouldBe "Instructions A"
            loader.loadFullFromCache(workspace, "skill-b") shouldBe "Instructions B"
        }
    }

    @Nested
    inner class UseSkillInstruction {

        @Test
        fun `prompt XML does NOT include use_skill instruction — TS-compatible format uses read tool`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Web search")
            ))
            // TS-compatible: instructions ("read its SKILL.md at <location> with `read`") 
            // are injected by PromptAssembler, not baked into the XML
            xml shouldNotContain "use_skill"
            xml shouldNotContain "MUST call"
        }

        @Test
        fun `prompt XML omits use_skill instruction regardless of always-on status`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("core", "Core rules", always = true, body = "Always do this.")
            ))
            xml shouldNotContain "use_skill"
        }
    }

    @Nested
    inner class TokenEfficiency {

        @Test
        fun `TS-compatible format uses child elements not self-closing tags`() {
            val loader = SkillLoader()
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("search", "Web search")
            ))
            xml shouldContain "</skill>"
            xml shouldNotContain "/>"
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

            xml shouldContain "<name>skill-1</name>"
            xml shouldContain "<name>skill-2</name>"
            xml shouldContain "<name>skill-3</name>"
            xml shouldNotContain "<name>skill-4</name>"
            xml shouldNotContain "<name>skill-5</name>"
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
            val loader = SkillLoader(skillsBlockCharLimit = 200)
            val xml = loader.buildPromptXml(listOf(
                makeSkillMeta("skill-one", "short"),
                makeSkillMeta("skill-two", "this description should push the second skill over the limit")
            ))

            xml shouldContain "<name>skill-one</name>"
            xml shouldNotContain "<name>skill-two</name>"
            xml shouldContain "</available_skills>"
        }

        @Test
        fun `skills within limits are unchanged`() {
            val loader = SkillLoader(maxSkillsCount = 10)
            val skills = (1..3).map { makeSkillMeta("skill-$it", "Description $it") }

            val xml = loader.buildPromptXml(skills)

            xml shouldContain "<name>skill-1</name>"
            xml shouldContain "<name>skill-2</name>"
            xml shouldContain "<name>skill-3</name>"
            xml shouldContain "<available_skills>"
            xml shouldContain "</available_skills>"
        }
    }

    @Nested
    inner class BodyPathResolution {

        @Test
        fun `relative script paths in body are expanded to absolute paths`() {
            val baseDir = Path.of("/home/user/.kclaw/workspace/skills/installer")
            val skill = makeSkillMeta(
                name = "installer",
                body = "Run: python3 scripts/install.py --url \"https://example.com\"",
                path = baseDir.resolve("SKILL.md"),
                resources = SkillResources(scripts = listOf("scripts/install.py"))
            ).copy(baseDir = baseDir)

            val resolved = SkillLoader.resolveBodyPaths(skill)

            resolved shouldContain "/home/user/.kclaw/workspace/skills/installer/scripts/install.py"
            // The bare relative path should no longer appear without the absolute prefix
            resolved shouldNotContain "python3 scripts/install.py"
        }

        @Test
        fun `multiple resource paths are all expanded`() {
            val baseDir = Path.of("/workspace/skills/pdf")
            val skill = makeSkillMeta(
                name = "pdf",
                body = """
                    Run scripts/convert.py for conversion.
                    See references/guide.md for details.
                    Template at assets/template.yml.
                """.trimIndent(),
                path = baseDir.resolve("SKILL.md"),
                resources = SkillResources(
                    scripts = listOf("scripts/convert.py"),
                    references = listOf("references/guide.md"),
                    assets = listOf("assets/template.yml")
                )
            ).copy(baseDir = baseDir)

            val resolved = SkillLoader.resolveBodyPaths(skill)

            resolved shouldContain "/workspace/skills/pdf/scripts/convert.py"
            resolved shouldContain "/workspace/skills/pdf/references/guide.md"
            resolved shouldContain "/workspace/skills/pdf/assets/template.yml"
        }

        @Test
        fun `body without matching resource paths is unchanged`() {
            val skill = makeSkillMeta(
                name = "simple",
                body = "Just follow these instructions. No scripts needed.",
                resources = SkillResources.EMPTY
            )

            val resolved = SkillLoader.resolveBodyPaths(skill)

            resolved shouldBe "Just follow these instructions. No scripts needed."
        }

        @Test
        fun `blank body is returned as-is`() {
            val skill = makeSkillMeta(
                name = "empty",
                body = "",
                resources = SkillResources(scripts = listOf("scripts/run.sh"))
            )

            SkillLoader.resolveBodyPaths(skill) shouldBe ""
        }

        @Test
        fun `snapshot body contains absolute paths after buildSnapshot`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            val skillDir = skillsDir.resolve("my-tool").createDirectories()
            val scriptsDir = skillDir.resolve("scripts").createDirectories()
            scriptsDir.resolve("run.sh").writeText("#!/bin/bash\necho hello")
            skillDir.resolve("SKILL.md").writeText(
                "---\nname: my-tool\ndescription: A tool\n---\nExecute: bash scripts/run.sh"
            )

            val gate = SkillGate(FakeEnvironmentChecker())
            val scanner = SkillScanner(gate)
            val loader = SkillLoader(scanner)
            val snapshot = loader.buildSnapshot(workspace)

            snapshot.skills.size shouldBe 1
            val body = snapshot.skills.first().body
            body shouldContain "scripts/run.sh"
            // The path should be absolute (contains the tempDir prefix)
            body shouldContain tempDir.toAbsolutePath().toString()
        }
    }

    @Nested
    inner class FingerprintStalenessCheck {

        @Test
        fun `unchanged roots produce equal fingerprints`() {
            val root = tempDir.resolve("skills").createDirectories()
            val skillDir = root.resolve("search").createDirectories()
            skillDir.resolve("SKILL.md").writeText("---\nname: search\n---\nBody")

            val fp1 = SkillRootsFingerprint.capture(listOf(root))
            val fp2 = SkillRootsFingerprint.capture(listOf(root))
            fp1 shouldBe fp2
        }

        @Test
        fun `new subdirectory changes fingerprint`() {
            val root = tempDir.resolve("skills").createDirectories()
            root.resolve("search").createDirectories()
                .resolve("SKILL.md").writeText("---\nname: search\n---\nBody")

            val fp1 = SkillRootsFingerprint.capture(listOf(root))

            root.resolve("deploy").createDirectories()
                .resolve("SKILL.md").writeText("---\nname: deploy\n---\nBody")

            val fp2 = SkillRootsFingerprint.capture(listOf(root))
            (fp1 == fp2) shouldBe false
        }

        @Test
        fun `modified file in existing subdirectory changes fingerprint`() {
            val root = tempDir.resolve("skills").createDirectories()
            val skillDir = root.resolve("search").createDirectories()
            skillDir.resolve("SKILL.md").writeText("---\nname: search\n---\nOriginal")

            val fp1 = SkillRootsFingerprint.capture(listOf(root))

            Thread.sleep(50) // ensure mtime granularity
            skillDir.resolve("SKILL.md").writeText("---\nname: search\n---\nUpdated")

            val fp2 = SkillRootsFingerprint.capture(listOf(root))
            (fp1 == fp2) shouldBe false
        }

        @Test
        fun `modified nested script changes fingerprint`() {
            val root = tempDir.resolve("skills").createDirectories()
            val skillDir = root.resolve("installer").createDirectories()
            skillDir.resolve("SKILL.md").writeText("---\nname: installer\n---\nBody")
            val scriptsDir = skillDir.resolve("scripts").createDirectories()
            scriptsDir.resolve("install.py").writeText("print('v1')")

            val fp1 = SkillRootsFingerprint.capture(listOf(root))

            Thread.sleep(50)
            scriptsDir.resolve("install.py").writeText("print('v2')")

            val fp2 = SkillRootsFingerprint.capture(listOf(root))
            (fp1 == fp2) shouldBe false
        }

        @Test
        fun `added file in nested directory changes fingerprint`() {
            val root = tempDir.resolve("skills").createDirectories()
            val skillDir = root.resolve("pdf").createDirectories()
            skillDir.resolve("SKILL.md").writeText("---\nname: pdf\n---\nBody")
            skillDir.resolve("references").createDirectories()

            val fp1 = SkillRootsFingerprint.capture(listOf(root))

            skillDir.resolve("references").resolve("guide.md").writeText("# Guide")

            val fp2 = SkillRootsFingerprint.capture(listOf(root))
            (fp1 == fp2) shouldBe false
        }

        @Test
        fun `non-existent root produces stable fingerprint`() {
            val missing = tempDir.resolve("does-not-exist")
            val fp1 = SkillRootsFingerprint.capture(listOf(missing))
            val fp2 = SkillRootsFingerprint.capture(listOf(missing))
            fp1 shouldBe fp2
        }

        @Test
        fun `multiple roots — change in any changes fingerprint`() {
            val root1 = tempDir.resolve("root1").createDirectories()
            val root2 = tempDir.resolve("root2").createDirectories()
            root1.resolve("search").createDirectories()
                .resolve("SKILL.md").writeText("---\nname: search\n---\n")

            val fp1 = SkillRootsFingerprint.capture(listOf(root1, root2))

            root2.resolve("deploy").createDirectories()
                .resolve("SKILL.md").writeText("---\nname: deploy\n---\n")

            val fp2 = SkillRootsFingerprint.capture(listOf(root1, root2))
            (fp1 == fp2) shouldBe false
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
        fun `status generates deterministic unique slash command names`() {
            val workspace = tempDir.resolve("workspace").createDirectories()
            val skillsDir = workspace.resolve("skills").createDirectories()
            createSkillFile(skillsDir, "hello-world", "Hello World")
            createSkillFile(skillsDir, "hello_world", "Hello_World")

            val loader = SkillLoader(SkillScanner(SkillGate(FakeEnvironmentChecker())))
            val report = loader.buildStatus(workspacePath = workspace)

            report.skills.map { it.invocation.commandName } shouldBe listOf("hello_world", "hello_world_2")
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
