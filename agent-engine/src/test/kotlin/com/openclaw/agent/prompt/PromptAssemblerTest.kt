package com.openclaw.agent.prompt

import com.openclaw.protocol.identifiers.ModelId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PromptAssemblerTest {

    /** Epoch 0 in UTC resolves to 1970-01-01 / 00:00 for deterministic assertions. */
    private val epoch = Instant.fromEpochMilliseconds(0)
    private val utc = TimeZone.of("UTC")

    private fun minimalContext() = PromptContext(
        currentTime = epoch,
        timeZone = utc,
        osInfo = "TestOS 1.0"
    )

    @Nested
    inner class SectionPresence {

        @Test
        fun `all populated fields produce all expected sections`() {
            val ctx = PromptContext(
                soul = "Be helpful.",
                agents = "Agent config",
                user = "Isaac",
                tools = "exec, fs",
                memory = "Previous sessions",
                identity = "Agent identity",
                bootstrap = "Bootstrap instructions",
                skillsXml = "<skills><skill name=\"search\" /></skills>",
                channelName = "telegram",
                channelCapabilities = "text, images",
                modelId = ModelId("anthropic:claude-sonnet-4-6"),
                currentTime = epoch,
                timeZone = utc,
                osInfo = "Linux 6.0"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "## BOOTSTRAP"
            result shouldContain "## SOUL"
            result shouldContain "## IDENTITY"
            result shouldContain "## AGENTS"
            result shouldContain "## USER"
            result shouldContain "## TOOLS"
            result shouldContain "<skills>"
            result shouldContain "## CHANNEL"
            result shouldContain "## MEMORY"
            result shouldContain "## RUNTIME"
        }

        @Test
        fun `empty optional fields cause those sections to be omitted`() {
            val ctx = minimalContext().copy(
                soul = "",
                agents = "",
                user = "",
                tools = "",
                memory = ""
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldNotContain "## SOUL"
            result shouldNotContain "## AGENTS"
            result shouldNotContain "## USER"
            result shouldNotContain "## TOOLS"
            result shouldNotContain "## MEMORY"
            // RUNTIME is always present because it always has content
            result shouldContain "## RUNTIME"
        }

        @Test
        fun `empty workspace with only defaults produces only runtime section`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            // No markdown section headers except RUNTIME
            result shouldContain "## RUNTIME"
            result shouldNotContain "## SOUL"
            result shouldNotContain "## AGENTS"
            result shouldNotContain "## USER"
            result shouldNotContain "## TOOLS"
            result shouldNotContain "## MEMORY"
            result shouldNotContain "## CHANNEL"
        }
    }

    @Nested
    inner class SectionOrder {

        @Test
        fun `sections appear in correct order BOOTSTRAP SOUL IDENTITY AGENTS USER TOOLS SKILLS CHANNEL MEMORY RUNTIME`() {
            val ctx = PromptContext(
                soul = "Soul",
                agents = "Agents",
                user = "User",
                tools = "Tools",
                memory = "Memory",
                identity = "Identity",
                bootstrap = "Bootstrap",
                skillsXml = "<skills />",
                channelName = "discord",
                currentTime = epoch,
                timeZone = utc,
                osInfo = "TestOS 1.0"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val bootstrapIdx = result.indexOf("## BOOTSTRAP")
            val soulIdx = result.indexOf("## SOUL")
            val identityIdx = result.indexOf("## IDENTITY")
            val agentsIdx = result.indexOf("## AGENTS")
            val userIdx = result.indexOf("## USER")
            val toolsIdx = result.indexOf("## TOOLS")
            val skillsIdx = result.indexOf("<skills />")
            val channelIdx = result.indexOf("## CHANNEL")
            val memoryIdx = result.indexOf("## MEMORY")
            val runtimeIdx = result.indexOf("## RUNTIME")

            (bootstrapIdx < soulIdx) shouldBe true
            (soulIdx < identityIdx) shouldBe true
            (identityIdx < agentsIdx) shouldBe true
            (agentsIdx < userIdx) shouldBe true
            (userIdx < toolsIdx) shouldBe true
            (toolsIdx < skillsIdx) shouldBe true
            (skillsIdx < channelIdx) shouldBe true
            (channelIdx < memoryIdx) shouldBe true
            (memoryIdx < runtimeIdx) shouldBe true
        }
    }

    @Nested
    inner class RuntimeSection {

        @Test
        fun `runtime section contains date derived from currentTime and timezone`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            // Epoch 0 in UTC is 1970-01-01
            result shouldContain "Date: 1970-01-01"
        }

        @Test
        fun `runtime section contains time derived from currentTime`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "Time: 00:00"
        }

        @Test
        fun `runtime section contains timezone id`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "Timezone: UTC"
        }

        @Test
        fun `runtime section contains model id when set`() {
            val ctx = minimalContext().copy(modelId = ModelId("anthropic:claude-sonnet-4-6"))
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "Model: anthropic:claude-sonnet-4-6"
        }

        @Test
        fun `runtime section omits model line when modelId is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "Model:"
        }

        @Test
        fun `runtime section contains OS info`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "OS: TestOS 1.0"
        }

        @Test
        fun `runtime section contains channel name when channelName is set`() {
            val ctx = minimalContext().copy(channelName = "slack")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            // Channel appears in RUNTIME block as well
            val runtimeStart = result.indexOf("## RUNTIME")
            val runtimeContent = result.substring(runtimeStart)
            runtimeContent shouldContain "Channel: slack"
        }

        @Test
        fun `runtime section omits channel line when channelName is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            val runtimeStart = result.indexOf("## RUNTIME")
            val runtimeContent = result.substring(runtimeStart)
            runtimeContent shouldNotContain "Channel:"
        }
    }

    @Nested
    inner class ChannelSection {

        @Test
        fun `channel section present when channelName is set`() {
            val ctx = minimalContext().copy(channelName = "telegram")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## CHANNEL"
            result shouldContain "Channel: telegram"
        }

        @Test
        fun `channel section absent when channelName is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## CHANNEL"
        }

        @Test
        fun `channel section includes capabilities when set`() {
            val ctx = minimalContext().copy(
                channelName = "discord",
                channelCapabilities = "text, embeds, reactions"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "Capabilities: text, embeds, reactions"
        }

        @Test
        fun `channel section omits capabilities line when channelCapabilities is null`() {
            val ctx = minimalContext().copy(channelName = "signal")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            val channelStart = result.indexOf("## CHANNEL")
            val afterChannel = result.substring(channelStart)
            afterChannel shouldNotContain "Capabilities:"
        }
    }

    @Nested
    inner class SkillsXml {

        @Test
        fun `skillsXml passed through as raw content without section header`() {
            val xmlPayload = "<skills><skill name=\"search\" enabled=\"true\" /></skills>"
            val ctx = minimalContext().copy(skillsXml = xmlPayload)
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain xmlPayload
            // Skills XML is raw — it must not be wrapped in a ## SKILLS header
            result shouldNotContain "## SKILLS"
        }

        @Test
        fun `empty skillsXml produces no raw skills block`() {
            val ctx = minimalContext().copy(skillsXml = "")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "<skills"
        }

        @Test
        fun `whitespace-only skillsXml is omitted`() {
            val ctx = minimalContext().copy(skillsXml = "   \n  ")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "<skills"
        }
    }

    @Nested
    inner class SectionSeparation {

        @Test
        fun `multiple sections are separated by double newline`() {
            val ctx = minimalContext().copy(soul = "Be helpful.", user = "Isaac")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            // Sections separated by exactly two newlines
            result shouldContain "\n\n"
        }

        @Test
        fun `section content follows header on next line`() {
            val ctx = minimalContext().copy(soul = "Be helpful.")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## SOUL\nBe helpful."
        }
    }

    @Nested
    inner class BootstrapSection {

        @Test
        fun `bootstrap section present when bootstrap is non-blank`() {
            val ctx = minimalContext().copy(bootstrap = "Initialize system")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## BOOTSTRAP"
            result shouldContain "Initialize system"
        }

        @Test
        fun `bootstrap section absent when bootstrap is blank`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## BOOTSTRAP"
        }

        @Test
        fun `bootstrap section appears before SOUL section`() {
            val ctx = minimalContext().copy(
                bootstrap = "Bootstrap content",
                soul = "Soul content"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            val bootstrapIdx = result.indexOf("## BOOTSTRAP")
            val soulIdx = result.indexOf("## SOUL")
            (bootstrapIdx < soulIdx) shouldBe true
        }

        @Test
        fun `bootstrap section omitted does not affect other section order`() {
            val ctx = minimalContext().copy(
                soul = "Soul",
                agents = "Agents",
                user = "User"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "## BOOTSTRAP"
            // SOUL should be the first section
            val soulIdx = result.indexOf("## SOUL")
            (soulIdx >= 0) shouldBe true
            val beforeSoul = result.substring(0, soulIdx).trim()
            beforeSoul shouldBe ""
        }
    }

    @Nested
    inner class IdentitySection {

        @Test
        fun `identity section present when identity is non-blank`() {
            val ctx = minimalContext().copy(identity = "I am a helpful assistant")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## IDENTITY"
            result shouldContain "I am a helpful assistant"
        }

        @Test
        fun `identity section absent when identity is blank`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## IDENTITY"
        }

        @Test
        fun `identity section appears after SOUL and before AGENTS`() {
            val ctx = minimalContext().copy(
                soul = "Soul content",
                identity = "Identity content",
                agents = "Agents content"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            val soulIdx = result.indexOf("## SOUL")
            val identityIdx = result.indexOf("## IDENTITY")
            val agentsIdx = result.indexOf("## AGENTS")
            (soulIdx < identityIdx) shouldBe true
            (identityIdx < agentsIdx) shouldBe true
        }
    }

    @Nested
    inner class HeartbeatField {

        @Test
        fun `heartbeat field is not emitted as a prompt section`() {
            val ctx = minimalContext().copy(heartbeat = "every 5 minutes")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "## HEARTBEAT"
        }
    }
}
