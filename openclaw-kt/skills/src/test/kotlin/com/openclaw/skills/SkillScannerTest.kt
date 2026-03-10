package com.openclaw.skills

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class SkillScannerTest {

    @TempDir
    lateinit var tempDir: Path

    /** Create a skill directory with a SKILL.md file. */
    private fun createSkill(
        baseDir: Path,
        dirName: String,
        name: String,
        description: String = "",
        body: String = "Skill body",
        bins: List<String> = emptyList(),
        os: List<String> = emptyList()
    ): Path {
        val skillDir = baseDir.resolve(dirName).createDirectories()
        val binsYaml = if (bins.isNotEmpty()) "\n  bins: [${bins.joinToString(", ")}]" else ""
        val osYaml = if (os.isNotEmpty()) "\n  os: [${os.joinToString(", ")}]" else ""
        val requiresYaml = if (binsYaml.isNotEmpty() || osYaml.isNotEmpty()) "\nrequires:$binsYaml$osYaml" else ""
        val descYaml = if (description.isNotEmpty()) "\ndescription: $description" else ""
        skillDir.resolve("SKILL.md").writeText(
            "---\nname: $name$descYaml$requiresYaml\n---\n$body"
        )
        return skillDir
    }

    private fun allEligibleGate(): SkillGate =
        SkillGate(FakeEnvironmentChecker(
            binaries = setOf("git", "docker", "kubectl", "npm"),
            envVars = setOf("TOKEN", "SECRET"),
            os = "linux"
        ))

    @Nested
    inner class DirectoryScanning {

        @Test
        fun `single skill found`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "search", "search", "Web search")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 1
            results[0].name shouldBe "search"
        }

        @Test
        fun `multiple skills found`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "search", "search")
            createSkill(dir, "deploy", "deploy")
            createSkill(dir, "review", "review")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 3
        }

        @Test
        fun `skips directories without SKILL_md`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "valid", "valid")
            dir.resolve("no-skill").createDirectories() // no SKILL.md

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 1
            results[0].name shouldBe "valid"
        }

        @Test
        fun `non-existent directory returns empty`() {
            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(tempDir.resolve("nonexistent")))
            results.shouldBeEmpty()
        }

        @Test
        fun `empty directory returns empty`() {
            val dir = tempDir.resolve("empty").createDirectories()
            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results.shouldBeEmpty()
        }
    }

    @Nested
    inner class Precedence {

        @Test
        fun `workspace overrides global with same name`() {
            val workspaceDir = tempDir.resolve("workspace-skills").createDirectories()
            val globalDir = tempDir.resolve("global-skills").createDirectories()

            createSkill(workspaceDir, "search", "search", description = "workspace version")
            createSkill(globalDir, "search", "search", description = "global version")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(workspaceDir, globalDir))
            results shouldHaveSize 1
            results[0].description shouldBe "workspace version"
        }

        @Test
        fun `first directory wins for duplicate names`() {
            val dir1 = tempDir.resolve("dir1").createDirectories()
            val dir2 = tempDir.resolve("dir2").createDirectories()

            createSkill(dir1, "deploy", "deploy", description = "first")
            createSkill(dir2, "deploy", "deploy", description = "second")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir1, dir2))
            results shouldHaveSize 1
            results[0].description shouldBe "first"
        }

        @Test
        fun `different names from multiple directories all included`() {
            val dir1 = tempDir.resolve("dir1").createDirectories()
            val dir2 = tempDir.resolve("dir2").createDirectories()

            createSkill(dir1, "search", "search")
            createSkill(dir2, "deploy", "deploy")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir1, dir2))
            results shouldHaveSize 2
        }
    }

    @Nested
    inner class GatingIntegration {

        @Test
        fun `ineligible skills excluded`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "linux-only", "linux-only", os = listOf("linux"))
            createSkill(dir, "windows-only", "windows-only", os = listOf("windows"))

            // Fake env is linux
            val gate = SkillGate(FakeEnvironmentChecker(os = "linux"))
            val scanner = SkillScanner(gate)
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 1
            results[0].name shouldBe "linux-only"
        }

        @Test
        fun `mix of eligible and ineligible returns only eligible`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "needs-git", "needs-git", bins = listOf("git"))
            createSkill(dir, "needs-docker", "needs-docker", bins = listOf("docker"))
            createSkill(dir, "no-reqs", "no-reqs")

            // Only git available
            val gate = SkillGate(FakeEnvironmentChecker(binaries = setOf("git")))
            val scanner = SkillScanner(gate)
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 2
            results.map { it.name }.toSet() shouldBe setOf("needs-git", "no-reqs")
        }
    }

    @Nested
    inner class ResourceDiscovery {

        @Test
        fun `scanned skill includes discovered resources`() {
            val dir = tempDir.resolve("skills").createDirectories()
            val skillDir = createSkill(dir, "scripted", "scripted")
            skillDir.resolve("scripts").createDirectories().resolve("run.sh").writeText("#!/bin/bash")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 1
            results[0].resources.hasScripts shouldBe true
            results[0].resources.scripts shouldBe listOf("run.sh")
        }

        @Test
        fun `scanned skill without resources has EMPTY resources`() {
            val dir = tempDir.resolve("skills").createDirectories()
            createSkill(dir, "plain", "plain")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scan(listOf(dir))
            results shouldHaveSize 1
            results[0].resources shouldBe SkillResources.EMPTY
        }
    }

    @Nested
    inner class Hardening {

        @Test
        fun `symlinked skill directory escaping root is rejected`() {
            val root = tempDir.resolve("skills").createDirectories()
            val outside = tempDir.resolve("outside").createDirectories()
            createSkill(outside, "escaped", "escaped")

            Files.createSymbolicLink(root.resolve("escaped-link"), outside.resolve("escaped"))

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scanDetailedSources(
                listOf(SkillDirectorySource(root, SkillSource.WORKSPACE))
            )

            results.shouldBeEmpty()
        }

        @Test
        fun `symlinked skill file escaping root is rejected`() {
            val root = tempDir.resolve("skills").createDirectories()
            val outside = tempDir.resolve("outside").createDirectories()
            val escapedFile = createSkill(outside, "escaped", "escaped").resolve("SKILL.md")
            val linkedDir = root.resolve("linked").createDirectories()

            Files.createSymbolicLink(linkedDir.resolve("SKILL.md"), escapedFile)

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scanDetailedSources(
                listOf(SkillDirectorySource(root, SkillSource.WORKSPACE))
            )

            results.shouldBeEmpty()
        }

        @Test
        fun `oversized skill files are skipped`() {
            val root = tempDir.resolve("skills").createDirectories()
            val skillDir = root.resolve("huge").createDirectories()
            val largeBody = "x".repeat(400)
            skillDir.resolve("SKILL.md").writeText("---\nname: huge\n---\n$largeBody")

            val scanner = SkillScanner(allEligibleGate())
            val results = scanner.scanDetailedSources(
                directories = listOf(SkillDirectorySource(root, SkillSource.WORKSPACE)),
                loadConfig = SkillsLoadConfig(maxSkillFileBytes = 32)
            )

            results.shouldBeEmpty()
        }
    }

    @Nested
    inner class DefaultDirectories {

        @Test
        fun `correct order from workspace path`() {
            val workspace = Path.of("/home/user/my-workspace")
            val dirs = SkillScanner.defaultDirectories(workspace)
            dirs shouldHaveSize 2
            dirs[0] shouldBe workspace.resolve("skills")
            dirs[1] shouldBe Path.of(System.getProperty("user.home"), ".openclawk", "skills")
        }

        @Test
        fun `includes bundled when provided`() {
            val workspace = Path.of("/home/user/ws")
            val bundled = Path.of("/opt/openclaw/skills")
            val dirs = SkillScanner.defaultDirectories(workspace, bundled)
            dirs shouldHaveSize 3
            dirs[2] shouldBe bundled
        }
    }
}
