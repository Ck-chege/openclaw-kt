package com.kclaw.agent.prompt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SystemPromptBuilderTest {

    @Nested
    inner class EmptyBuilder {

        @Test
        fun `empty builder produces empty string`() {
            val result = SystemPromptBuilder().build()
            result shouldBe ""
        }

        @Test
        fun `top-level systemPrompt with no calls produces empty string`() {
            val result = systemPrompt { }
            result shouldBe ""
        }
    }

    @Nested
    inner class Section {

        @Test
        fun `single section has correct header prefix and content`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "You are a helpful assistant." }
            }.build()
            result shouldBe "## SOUL\nYou are a helpful assistant."
        }

        @Test
        fun `blank content section is omitted`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "" }
            }.build()
            result shouldBe ""
        }

        @Test
        fun `whitespace-only content section is omitted`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "   \n  \t  " }
            }.build()
            result shouldBe ""
        }

        @Test
        fun `multiple sections joined by double newline`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "Soul content" }
                section("AGENTS") { "Agent content" }
                section("USER") { "User content" }
            }.build()
            result shouldBe "## SOUL\nSoul content\n\n## AGENTS\nAgent content\n\n## USER\nUser content"
        }

        @Test
        fun `blank section between two present sections is skipped without extra newlines`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "Soul content" }
                section("EMPTY") { "" }
                section("USER") { "User content" }
            }.build()
            result shouldBe "## SOUL\nSoul content\n\n## USER\nUser content"
            result shouldNotContain "EMPTY"
        }

        @Test
        fun `section name appears in output as header`() {
            val result = SystemPromptBuilder().apply {
                section("MY_SECTION") { "data" }
            }.build()
            result shouldContain "## MY_SECTION"
        }
    }

    @Nested
    inner class Raw {

        @Test
        fun `raw adds content verbatim without section header`() {
            val result = SystemPromptBuilder().apply {
                raw("<skills><skill name=\"search\" /></skills>")
            }.build()
            result shouldBe "<skills><skill name=\"search\" /></skills>"
        }

        @Test
        fun `raw blank content is omitted`() {
            val result = SystemPromptBuilder().apply {
                raw("")
            }.build()
            result shouldBe ""
        }

        @Test
        fun `raw whitespace-only content is omitted`() {
            val result = SystemPromptBuilder().apply {
                raw("   \n  ")
            }.build()
            result shouldBe ""
        }

        @Test
        fun `raw content interleaved with sections respects double-newline separator`() {
            val result = SystemPromptBuilder().apply {
                section("SOUL") { "Soul" }
                raw("<skills />")
                section("MEMORY") { "Mem" }
            }.build()
            result shouldBe "## SOUL\nSoul\n\n<skills />\n\n## MEMORY\nMem"
        }
    }

    @Nested
    inner class XmlDsl {

        @Test
        fun `xml with no elements produces empty output and is omitted`() {
            val result = SystemPromptBuilder().apply {
                xml("skills") { }
            }.build()
            result shouldBe ""
        }

        @Test
        fun `xml with one self-closing element renders correctly`() {
            val result = SystemPromptBuilder().apply {
                xml("skills") {
                    element("skill") {
                        attribute("name", "search")
                    }
                }
            }.build()
            result shouldBe "<skills>\n  <skill name=\"search\" />\n</skills>"
        }

        @Test
        fun `xml element with text content renders open and close tags`() {
            val result = SystemPromptBuilder().apply {
                xml("tools") {
                    element("tool") {
                        attribute("id", "exec")
                        text("Run shell commands")
                    }
                }
            }.build()
            result shouldBe "<tools>\n  <tool id=\"exec\">Run shell commands</tool>\n</tools>"
        }

        @Test
        fun `xml element with multiple attributes renders all in order`() {
            val result = SystemPromptBuilder().apply {
                xml("skills") {
                    element("skill") {
                        attribute("name", "browser")
                        attribute("version", "2")
                        attribute("enabled", "true")
                    }
                }
            }.build()
            result shouldContain "name=\"browser\""
            result shouldContain "version=\"2\""
            result shouldContain "enabled=\"true\""
        }

        @Test
        fun `xml with multiple elements renders all separated by newline`() {
            val result = SystemPromptBuilder().apply {
                xml("skills") {
                    element("skill") { attribute("name", "a") }
                    element("skill") { attribute("name", "b") }
                }
            }.build()
            result shouldContain "  <skill name=\"a\" />"
            result shouldContain "  <skill name=\"b\" />"
        }

        @Test
        fun `xml element with no attributes and no text renders self-closing`() {
            val result = SystemPromptBuilder().apply {
                xml("items") {
                    element("item") { }
                }
            }.build()
            result shouldContain "  <item />"
        }
    }

    @Nested
    inner class TopLevelFunction {

        @Test
        fun `systemPrompt top-level function delegates to builder correctly`() {
            val result = systemPrompt {
                section("SOUL") { "Be helpful." }
                section("USER") { "Isaac" }
            }
            result shouldBe "## SOUL\nBe helpful.\n\n## USER\nIsaac"
        }

        @Test
        fun `systemPrompt top-level function with raw and xml`() {
            val result = systemPrompt {
                section("HEADER") { "Intro" }
                raw("<note>verbatim</note>")
                xml("items") {
                    element("item") { attribute("id", "1"); text("first") }
                }
            }
            result shouldContain "## HEADER\nIntro"
            result shouldContain "<note>verbatim</note>"
            result shouldContain "<items>"
            result shouldContain "  <item id=\"1\">first</item>"
        }
    }
}
