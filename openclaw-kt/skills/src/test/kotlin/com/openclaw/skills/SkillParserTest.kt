package com.openclaw.skills

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class SkillParserTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeSkill(content: String): Path {
        val skillDir = tempDir.resolve("test-skill")
        skillDir.toFile().mkdirs()
        val skillFile = skillDir.resolve("SKILL.md")
        skillFile.writeText(content)
        return skillFile
    }

    @Nested
    inner class ValidParsing {

        @Test
        fun `minimal frontmatter with name only`() {
            val path = writeSkill(
                """
                |---
                |name: search
                |---
                |Use this skill to search the web.
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.name shouldBe "search"
            result.description shouldBe ""
            result.emoji.shouldBeNull()
            result.always shouldBe false
        }

        @Test
        fun `full frontmatter with all fields`() {
            val path = writeSkill(
                """
                |---
                |name: code-review
                |description: Reviews code for best practices
                |emoji: "\uD83D\uDD0D"
                |always: false
                |requires:
                |  bins: [git, gh]
                |  env: [GITHUB_TOKEN]
                |  os: [linux, macos]
                |openclaw:
                |  version: "2.0"
                |  tags: [dev, review]
                |---
                |Review the code thoroughly.
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.name shouldBe "code-review"
            result.description shouldBe "Reviews code for best practices"
            result.requires.bins shouldBe listOf("git", "gh")
            result.requires.env shouldBe listOf("GITHUB_TOKEN")
            result.requires.os shouldBe listOf("linux", "macos")
            result.frontmatter.openclaw.version shouldBe "2.0"
            result.frontmatter.openclaw.tags shouldBe listOf("dev", "review")
        }

        @Test
        fun `requires with bins and env`() {
            val path = writeSkill(
                """
                |---
                |name: docker-deploy
                |requires:
                |  bins: [docker, kubectl]
                |  env: [DOCKER_HOST, KUBECONFIG]
                |---
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.requires.bins shouldBe listOf("docker", "kubectl")
            result.requires.env shouldBe listOf("DOCKER_HOST", "KUBECONFIG")
        }

        @Test
        fun `requires with anyBins`() {
            val path = writeSkill(
                """
                |---
                |name: package-manager
                |requires:
                |  any_bins: [npm, yarn, pnpm]
                |---
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.requires.anyBins shouldBe listOf("npm", "yarn", "pnpm")
        }

        @Test
        fun `os restriction`() {
            val path = writeSkill(
                """
                |---
                |name: macos-only
                |requires:
                |  os: [macos]
                |---
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.requires.os shouldBe listOf("macos")
        }

        @Test
        fun `always flag set to true`() {
            val path = writeSkill(
                """
                |---
                |name: core-instructions
                |always: true
                |---
                |Always follow these instructions.
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.always shouldBe true
        }

        @Test
        fun `emoji field parsed`() {
            val path = writeSkill(
                """
                |---
                |name: search
                |emoji: "\uD83D\uDD0E"
                |---
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.emoji shouldBe "\uD83D\uDD0E"
        }

        @Test
        fun `missing optional fields use defaults`() {
            val path = writeSkill(
                """
                |---
                |name: minimal
                |---
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.description shouldBe ""
            result.emoji.shouldBeNull()
            result.always shouldBe false
            result.requires.bins shouldBe emptyList()
            result.requires.anyBins shouldBe emptyList()
            result.requires.env shouldBe emptyList()
            result.requires.os shouldBe emptyList()
            result.frontmatter.openclaw.version shouldBe "1.0"
            result.frontmatter.openclaw.tags shouldBe emptyList()
        }
    }

    @Nested
    inner class MalformedInput {

        @Test
        fun `missing delimiters returns null`() {
            val path = writeSkill("name: search\nThis is just text.")
            SkillParser.parse(path).shouldBeNull()
        }

        @Test
        fun `no opening delimiter returns null`() {
            val path = writeSkill(
                """
                |name: search
                |---
                |Body text
                """.trimMargin()
            )
            SkillParser.parse(path).shouldBeNull()
        }

        @Test
        fun `blank name returns null`() {
            val path = writeSkill(
                """
                |---
                |name: "   "
                |---
                """.trimMargin()
            )
            SkillParser.parse(path).shouldBeNull()
        }

        @Test
        fun `invalid YAML returns null`() {
            val path = writeSkill(
                """
                |---
                |name: [invalid: yaml: :::
                |---
                """.trimMargin()
            )
            SkillParser.parse(path).shouldBeNull()
        }

        @Test
        fun `missing name field returns null`() {
            val path = writeSkill(
                """
                |---
                |description: No name here
                |---
                """.trimMargin()
            )
            SkillParser.parse(path).shouldBeNull()
        }

        @Test
        fun `unknown YAML keys tolerated`() {
            val path = writeSkill(
                """
                |---
                |name: tolerant
                |unknown_field: should not crash
                |another_unknown:
                |  nested: value
                |---
                |Body
                """.trimMargin()
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.name shouldBe "tolerant"
        }
    }

    @Nested
    inner class BodyExtraction {

        @Test
        fun `content after frontmatter is extracted`() {
            val path = writeSkill(
                """
                |---
                |name: test
                |---
                |This is the body content.
                |It spans multiple lines.
                """.trimMargin()
            )
            val body = SkillParser.extractBody(path)
            body shouldBe "This is the body content.\nIt spans multiple lines."
        }

        @Test
        fun `trims whitespace from body`() {
            val path = writeSkill(
                """
                |---
                |name: test
                |---
                |
                |   Body with whitespace
                |
                """.trimMargin()
            )
            val body = SkillParser.extractBody(path)
            body shouldBe "Body with whitespace"
        }

        @Test
        fun `full content returned when no frontmatter`() {
            val path = writeSkill("Just plain text content.\nNo frontmatter here.")
            val body = SkillParser.extractBody(path)
            body shouldBe "Just plain text content.\nNo frontmatter here."
        }

        @Test
        fun `empty body for frontmatter-only file`() {
            val path = writeSkill(
                """
                |---
                |name: empty-body
                |---
                """.trimMargin()
            )
            val body = SkillParser.extractBody(path)
            body.shouldBeEmpty()
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `non-existent file returns null`() {
            val fakePath = tempDir.resolve("nonexistent/SKILL.md")
            SkillParser.parse(fakePath).shouldBeNull()
        }

        @Test
        fun `file with extra whitespace around delimiters`() {
            val path = writeSkill(
                "  ---  \nname: spacey\n  ---  \nBody after spacey delimiters."
            )
            val result = SkillParser.parse(path)
            result.shouldNotBeNull()
            result.name shouldBe "spacey"
            result.body shouldBe "Body after spacey delimiters."
        }
    }
}
