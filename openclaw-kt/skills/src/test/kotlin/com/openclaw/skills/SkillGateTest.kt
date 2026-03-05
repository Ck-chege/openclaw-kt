package com.openclaw.skills

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
        os: List<String> = emptyList()
    ): SkillMeta = SkillMeta(
        frontmatter = SkillFrontmatter(
            name = name,
            requires = SkillRequirements(bins = bins, anyBins = anyBins, env = env, os = os)
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
