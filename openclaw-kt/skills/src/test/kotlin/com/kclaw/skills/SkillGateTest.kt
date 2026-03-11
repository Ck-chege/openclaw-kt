package com.kclaw.skills

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SkillGateTest {

    private fun skill(
        name: String = "test-skill",
        bins: List<String> = emptyList(),
        anyBins: List<String> = emptyList(),
        env: List<String> = emptyList(),
        os: List<String> = emptyList(),
        config: List<String> = emptyList()
    ): SkillMeta = SkillMeta(
        frontmatter = SkillFrontmatter(
            name = name,
            requires = SkillRequirements(
                bins = bins,
                anyBins = anyBins,
                env = env,
                os = os,
                config = config
            )
        ),
        path = Path.of("/fake/SKILL.md"),
        body = ""
    )

    @Nested
    inner class OsGating {

        @Test
        fun `no os restriction means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(os = "linux"))
            gate.evaluate(skill()) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `matching os means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(os = "macos"))
            gate.evaluate(skill(os = listOf("linux", "macos"))) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `non-matching os means ineligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(os = "windows"))
            val result = gate.evaluate(skill(os = listOf("linux", "macos")))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "OS 'windows' not in required: [linux, macos]"
        }
    }

    @Nested
    inner class BinaryGating {

        @Test
        fun `no bins requirement means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker())
            gate.evaluate(skill()) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `all bins present means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = setOf("git", "gh")))
            gate.evaluate(skill(bins = listOf("git", "gh"))) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `one bin missing means ineligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = setOf("git")))
            val result = gate.evaluate(skill(bins = listOf("git", "gh")))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "Missing required binaries: [gh]"
        }
    }

    @Nested
    inner class AnyBinsGating {

        @Test
        fun `empty anyBins means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker())
            gate.evaluate(skill()) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `one of anyBins present means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = setOf("yarn")))
            gate.evaluate(skill(anyBins = listOf("npm", "yarn", "pnpm"))) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `none of anyBins present means ineligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = emptySet()))
            val result = gate.evaluate(skill(anyBins = listOf("npm", "yarn", "pnpm")))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "None of anyBins present: [npm, yarn, pnpm]"
        }
    }

    @Nested
    inner class EnvVarGating {

        @Test
        fun `no env requirement means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker())
            gate.evaluate(skill()) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `all env vars set means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(envVars = setOf("API_KEY", "SECRET")))
            gate.evaluate(skill(env = listOf("API_KEY", "SECRET"))) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `one env var missing means ineligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(envVars = setOf("API_KEY")))
            val result = gate.evaluate(skill(env = listOf("API_KEY", "SECRET")))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "Missing required env vars: [SECRET]"
        }
    }

    @Nested
    inner class ConfigGating {

        @Test
        fun `all config paths satisfied means eligible`() {
            val gate = SkillGate(isConfigPathTruthy = { path -> path in setOf("providers.azure.endpoint", "secrets.openaiApiKey") })
            gate.evaluate(skill(config = listOf("providers.azure.endpoint", "secrets.openaiApiKey"))) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `missing config path means ineligible`() {
            val gate = SkillGate(isConfigPathTruthy = { path -> path == "providers.azure.endpoint" })
            val result = gate.evaluate(skill(config = listOf("providers.azure.endpoint", "secrets.openaiApiKey")))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "Missing required config paths: [secrets.openaiApiKey]"
            result.missing.config shouldBe listOf("secrets.openaiApiKey")
        }
    }

    @Nested
    inner class AlwaysFlag {

        @Test
        fun `always true skill is eligible even with missing binaries`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = emptySet()))
            val alwaysSkill = SkillMeta(
                frontmatter = SkillFrontmatter(
                    name = "always-skill",
                    always = true,
                    requires = SkillRequirements(bins = listOf("missing-binary"))
                ),
                path = Path.of("/fake/SKILL.md"),
                body = ""
            )
            gate.evaluate(alwaysSkill) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `always true skill is eligible even with missing env vars`() {
            val gate = SkillGate(FakeEnvironmentChecker(envVars = emptySet()))
            val alwaysSkill = SkillMeta(
                frontmatter = SkillFrontmatter(
                    name = "always-skill",
                    always = true,
                    requires = SkillRequirements(env = listOf("MISSING_VAR"))
                ),
                path = Path.of("/fake/SKILL.md"),
                body = ""
            )
            gate.evaluate(alwaysSkill) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `always true skill is eligible even with wrong OS`() {
            val gate = SkillGate(FakeEnvironmentChecker(os = "linux"))
            val alwaysSkill = SkillMeta(
                frontmatter = SkillFrontmatter(
                    name = "always-skill",
                    always = true,
                    requires = SkillRequirements(os = listOf("windows"))
                ),
                path = Path.of("/fake/SKILL.md"),
                body = ""
            )
            gate.evaluate(alwaysSkill) shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `always false skill is still gated normally`() {
            val gate = SkillGate(FakeEnvironmentChecker(binaries = emptySet()))
            val normalSkill = SkillMeta(
                frontmatter = SkillFrontmatter(
                    name = "normal-skill",
                    always = false,
                    requires = SkillRequirements(bins = listOf("missing-binary"))
                ),
                path = Path.of("/fake/SKILL.md"),
                body = ""
            )
            gate.evaluate(normalSkill).shouldBeInstanceOf<SkillGateResult.Ineligible>()
        }
    }

    @Nested
    inner class Combined {

        @Test
        fun `all checks pass means eligible`() {
            val gate = SkillGate(FakeEnvironmentChecker(
                binaries = setOf("git", "docker"),
                envVars = setOf("TOKEN"),
                os = "linux"
            ))
            val result = gate.evaluate(skill(
                bins = listOf("git", "docker"),
                env = listOf("TOKEN"),
                os = listOf("linux")
            ))
            result shouldBe SkillGateResult.Eligible
        }

        @Test
        fun `os fails first even if bins would pass`() {
            val gate = SkillGate(FakeEnvironmentChecker(
                binaries = setOf("git"),
                envVars = setOf("TOKEN"),
                os = "windows"
            ))
            val result = gate.evaluate(skill(
                bins = listOf("git"),
                env = listOf("TOKEN"),
                os = listOf("linux")
            ))
            result.shouldBeInstanceOf<SkillGateResult.Ineligible>()
            result.reason shouldBe "OS 'windows' not in required: [linux]"
        }
    }
}
