package com.kclaw.agent.prompt

import com.kclaw.protocol.identifiers.ChatType
import com.kclaw.protocol.identifiers.ModelId
import com.kclaw.protocol.identifiers.SessionKey
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

    private fun fullContext() = PromptContext(
        soul = "Be helpful.",
        agents = "Agent config",
        user = "Isaac",
        tools = "exec, fs",
        memory = "Previous sessions",
        identity = "Agent identity",
        bootstrap = "Bootstrap instructions",
        heartbeat = "Check email daily",
        toolDescriptions = listOf(
            ToolDescription("read", "Read file contents"),
            ToolDescription("write", "Write file contents")
        ),
        authorizedSender = "Isaac (owner)",
        skillsXml = "<skills><skill name=\"search\" /></skills>",
        channelName = "telegram",
        channelCapabilities = "text, images",
        modelId = ModelId("anthropic:claude-sonnet-4-6"),
        currentTime = epoch,
        timeZone = utc,
        osInfo = "Linux 6.0"
    )

    @Nested
    inner class IdentityLine {

        @Test
        fun `prompt starts with identity line`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result.trimStart().startsWith("You are a personal assistant running inside KClaw.") shouldBe true
        }

        @Test
        fun `identity line present in all prompt modes`() {
            for (mode in PromptMode.entries) {
                val ctx = minimalContext().copy(promptMode = mode)
                val result = PromptAssembler.assembleSystemPrompt(ctx)
                result shouldContain "You are a personal assistant running inside KClaw."
            }
        }
    }

    @Nested
    inner class Zone1SystemInstructions {

        @Test
        fun `tool descriptions section present when tools provided`() {
            val ctx = minimalContext().copy(
                toolDescriptions = listOf(
                    ToolDescription("read", "Read file contents"),
                    ToolDescription("exec", "Execute shell command")
                )
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## Tooling"
            result shouldContain "- read: Read file contents"
            result shouldContain "- exec: Execute shell command"
        }

        @Test
        fun `tool descriptions section absent when no tools`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## Tooling"
        }

        @Test
        fun `tool call style directive present when tools provided`() {
            val ctx = minimalContext().copy(
                toolDescriptions = listOf(ToolDescription("read", "Read"))
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "do not narrate routine"
        }

        @Test
        fun `safety section always present`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "## Safety"
            result shouldContain "do not pursue self-preservation"
        }

        @Test
        fun `authorized senders section present when set`() {
            val ctx = minimalContext().copy(authorizedSender = "Isaac (admin)")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## Authorized Senders"
            result shouldContain "Authorized senders: Isaac (admin)"
        }

        @Test
        fun `authorized senders section absent when null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## Authorized Senders"
        }

        @Test
        fun `date and time section present`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "## Current Date & Time"
            result shouldContain "Time zone: UTC"
        }
    }

    @Nested
    inner class Zone2WorkspaceFiles {

        @Test
        fun `workspace header present when any workspace file has content`() {
            val ctx = minimalContext().copy(soul = "Be helpful.")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "# Project Context"
        }

        @Test
        fun `workspace files use dot-md suffixed headers`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## AGENTS.md"
            result shouldContain "## SOUL.md"
            result shouldContain "## TOOLS.md"
            result shouldContain "## IDENTITY.md"
            result shouldContain "## USER.md"
            result shouldContain "## HEARTBEAT.md"
            result shouldContain "## BOOTSTRAP.md"
            result shouldContain "## MEMORY.md"
        }

        @Test
        fun `empty workspace files are omitted`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "## AGENTS.md"
            result shouldNotContain "## SOUL.md"
            result shouldNotContain "## TOOLS.md"
            result shouldNotContain "## IDENTITY.md"
            result shouldNotContain "## USER.md"
        }

        @Test
        fun `workspace file order is SOUL AGENTS TOOLS IDENTITY USER HEARTBEAT BOOTSTRAP MEMORY`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val soulIdx = result.indexOf("## SOUL.md")
            val agentsIdx = result.indexOf("## AGENTS.md")
            val toolsIdx = result.indexOf("## TOOLS.md")
            val identityIdx = result.indexOf("## IDENTITY.md")
            val userIdx = result.indexOf("## USER.md")
            val heartbeatIdx = result.indexOf("## HEARTBEAT.md")
            val bootstrapIdx = result.indexOf("## BOOTSTRAP.md")
            val memoryIdx = result.indexOf("## MEMORY.md")

            // SOUL.md first — sets personality before ops manual (AGENTS.md)
            (soulIdx < agentsIdx) shouldBe true
            (agentsIdx < toolsIdx) shouldBe true
            (toolsIdx < identityIdx) shouldBe true
            (identityIdx < userIdx) shouldBe true
            (userIdx < heartbeatIdx) shouldBe true
            (heartbeatIdx < bootstrapIdx) shouldBe true
            (bootstrapIdx < memoryIdx) shouldBe true
        }

        @Test
        fun `bootstrap section absent when bootstrap is blank`() {
            val ctx = minimalContext().copy(soul = "Soul")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "## BOOTSTRAP.md"
        }

        @Test
        fun `heartbeat section absent when heartbeat is blank`() {
            val ctx = minimalContext().copy(soul = "Soul")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "## HEARTBEAT.md"
        }
    }

    @Nested
    inner class MemorySecurityBoundary {

        @Test
        fun `memory included in main session`() {
            val ctx = minimalContext().copy(
                memory = "Important memories",
                sessionType = SessionType.MAIN
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## MEMORY.md"
            result shouldContain "Important memories"
        }

        @Test
        fun `memory excluded in group session`() {
            val ctx = minimalContext().copy(
                memory = "Private stuff",
                sessionType = SessionType.GROUP
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldNotContain "## MEMORY.md"
            result shouldNotContain "Private stuff"
        }

        @Test
        fun `memory included in subagent session`() {
            val ctx = minimalContext().copy(
                memory = "Context for sub",
                sessionType = SessionType.SUBAGENT,
                promptMode = PromptMode.FULL
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## MEMORY.md"
        }

        @Test
        fun `memory included in cron session`() {
            val ctx = minimalContext().copy(
                memory = "Cron context",
                sessionType = SessionType.CRON,
                promptMode = PromptMode.FULL
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## MEMORY.md"
        }
    }

    @Nested
    inner class ZoneOrdering {

        @Test
        fun `zone 1 appears before zone 2`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val safetyIdx = result.indexOf("## Safety")
            val workspaceIdx = result.indexOf("# Project Context")

            (safetyIdx < workspaceIdx) shouldBe true
        }

        @Test
        fun `zone 2 appears before zone 3`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val workspaceIdx = result.indexOf("# Project Context")
            val runtimeIdx = result.indexOf("## Runtime")

            (workspaceIdx < runtimeIdx) shouldBe true
        }

        @Test
        fun `identity line appears before everything else`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val identityLineIdx = result.indexOf("You are a personal assistant running inside KClaw")
            val safetyIdx = result.indexOf("## Safety")
            val workspaceIdx = result.indexOf("# Project Context")

            (identityLineIdx < safetyIdx) shouldBe true
            (identityLineIdx < workspaceIdx) shouldBe true
        }

        @Test
        fun `tool descriptions appear before safety`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val toolsIdx = result.indexOf("## Tooling")
            val safetyIdx = result.indexOf("## Safety")

            (toolsIdx < safetyIdx) shouldBe true
        }

        @Test
        fun `skills xml appears after safety and before workspace`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val safetyIdx = result.indexOf("## Safety")
            val skillsIdx = result.indexOf("<skills>")
            val workspaceIdx = result.indexOf("# Project Context")

            (safetyIdx < skillsIdx) shouldBe true
            (skillsIdx < workspaceIdx) shouldBe true
        }

        @Test
        fun `date and time appears before project context`() {
            val ctx = fullContext()
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            val dateIdx = result.indexOf("## Current Date & Time")
            val workspaceIdx = result.indexOf("# Project Context")

            (dateIdx < workspaceIdx) shouldBe true
        }
    }

    @Nested
    inner class RuntimeSection {

        @Test
        fun `runtime section contains model id when set`() {
            val ctx = minimalContext().copy(modelId = ModelId("anthropic:claude-sonnet-4-6"))
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "model=anthropic:claude-sonnet-4-6"
        }

        @Test
        fun `runtime section omits model when modelId is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "model="
        }

        @Test
        fun `runtime section contains OS info`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldContain "os=TestOS 1.0"
        }

        @Test
        fun `runtime section contains channel name when set`() {
            val ctx = minimalContext().copy(channelName = "slack")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "channel=slack"
        }

        @Test
        fun `runtime section omits channel when channelName is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "channel="
        }
    }

    @Nested
    inner class ChannelSection {

        @Test
        fun `channel name appears in runtime line when set`() {
            val ctx = minimalContext().copy(channelName = "telegram")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "channel=telegram"
        }

        @Test
        fun `channel absent from runtime when channelName is null`() {
            val result = PromptAssembler.assembleSystemPrompt(minimalContext())
            result shouldNotContain "channel="
        }

        @Test
        fun `channel capabilities not in system prompt (reserved for future channel section)`() {
            val ctx = minimalContext().copy(
                channelName = "discord",
                channelCapabilities = "text, embeds, reactions"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            // capabilities are not yet injected until channel section is implemented
            result shouldContain "channel=discord"
        }
    }

    @Nested
    inner class SkillsXml {

        @Test
        fun `skillsXml passed through as raw content`() {
            val xmlPayload = "<skills><skill name=\"search\" enabled=\"true\" /></skills>"
            val ctx = minimalContext().copy(skillsXml = xmlPayload)
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain xmlPayload
        }

        @Test
        fun `empty skillsXml produces no skills block`() {
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
    inner class PromptModes {

        @Test
        fun `minimal mode excludes heartbeat bootstrap and memory`() {
            val ctx = fullContext().copy(promptMode = PromptMode.MINIMAL)
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldNotContain "## HEARTBEAT.md"
            result shouldNotContain "## BOOTSTRAP.md"
            result shouldNotContain "## MEMORY.md"
        }

        @Test
        fun `minimal mode includes core workspace files`() {
            val ctx = fullContext().copy(promptMode = PromptMode.MINIMAL)
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "## AGENTS.md"
            result shouldContain "## SOUL.md"
            result shouldContain "## TOOLS.md"
            result shouldContain "## IDENTITY.md"
            result shouldContain "## USER.md"
        }

        @Test
        fun `minimal mode includes system instructions`() {
            val ctx = fullContext().copy(promptMode = PromptMode.MINIMAL)
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "You are a personal assistant running inside KClaw"
            result shouldContain "## Safety"
            result shouldContain "## Tooling"
        }

        @Test
        fun `none mode only has identity line and tools`() {
            val ctx = fullContext().copy(promptMode = PromptMode.NONE)
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "You are a personal assistant running inside KClaw"
            result shouldContain "## Tooling"
            result shouldNotContain "## Safety"
            result shouldNotContain "# Project Context"
            result shouldNotContain "## Runtime"
        }

        @Test
        fun `none mode without tools is just identity line`() {
            val ctx = minimalContext().copy(promptMode = PromptMode.NONE)
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result.trim() shouldBe "You are a personal assistant running inside KClaw."
        }
    }

    @Nested
    inner class SectionSeparation {

        @Test
        fun `multiple sections are separated by double newline`() {
            val ctx = minimalContext().copy(soul = "Be helpful.", user = "Isaac")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "\n\n"
        }

        @Test
        fun `section content follows header`() {
            val ctx = minimalContext().copy(soul = "Be helpful.")
            val result = PromptAssembler.assembleSystemPrompt(ctx)
            result shouldContain "## SOUL.md"
            result shouldContain "Be helpful."
        }
    }

    @Nested
    inner class DeriveSessionType {

        @Test
        fun `subagent key derives SUBAGENT`() {
            val key = SessionKey.subagent(SessionKey.main(), "sub123")
            PromptContextDefaults.deriveSessionType(key) shouldBe SessionType.SUBAGENT
        }

        @Test
        fun `cron key derives CRON`() {
            val key = SessionKey.cron("main", "job1")
            PromptContextDefaults.deriveSessionType(key) shouldBe SessionType.CRON
        }

        @Test
        fun `GROUP chatType derives GROUP`() {
            val key = SessionKey.dm("main", "telegram", "user1")
            PromptContextDefaults.deriveSessionType(key, ChatType.GROUP) shouldBe SessionType.GROUP
        }

        @Test
        fun `DM chatType on normal key derives MAIN`() {
            val key = SessionKey.dm("main", "telegram", "user1")
            PromptContextDefaults.deriveSessionType(key, ChatType.DM) shouldBe SessionType.MAIN
        }

        @Test
        fun `null chatType on normal key derives MAIN`() {
            val key = SessionKey.main()
            PromptContextDefaults.deriveSessionType(key) shouldBe SessionType.MAIN
        }

        @Test
        fun `subagent takes priority over GROUP chatType`() {
            val key = SessionKey.subagent(SessionKey.main(), "sub1")
            PromptContextDefaults.deriveSessionType(key, ChatType.GROUP) shouldBe SessionType.SUBAGENT
        }

        @Test
        fun `cron takes priority over GROUP chatType`() {
            val key = SessionKey.cron("main", "job1")
            PromptContextDefaults.deriveSessionType(key, ChatType.GROUP) shouldBe SessionType.CRON
        }
    }

    @Nested
    inner class DerivePromptMode {

        @Test
        fun `SUBAGENT derives MINIMAL`() {
            PromptContextDefaults.derivePromptMode(SessionType.SUBAGENT) shouldBe PromptMode.MINIMAL
        }

        @Test
        fun `CRON derives MINIMAL`() {
            PromptContextDefaults.derivePromptMode(SessionType.CRON) shouldBe PromptMode.MINIMAL
        }

        @Test
        fun `MAIN derives FULL`() {
            PromptContextDefaults.derivePromptMode(SessionType.MAIN) shouldBe PromptMode.FULL
        }

        @Test
        fun `GROUP derives FULL`() {
            PromptContextDefaults.derivePromptMode(SessionType.GROUP) shouldBe PromptMode.FULL
        }
    }

    @Nested
    inner class SessionTypeIntegration {

        @Test
        fun `subagent session produces minimal prompt without HEARTBEAT BOOTSTRAP MEMORY`() {
            val ctx = fullContext().copy(
                sessionType = SessionType.SUBAGENT,
                promptMode = PromptMode.MINIMAL
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "## AGENTS.md"
            result shouldContain "## SOUL.md"
            result shouldContain "## TOOLS.md"
            result shouldContain "## IDENTITY.md"
            result shouldContain "## USER.md"
            result shouldNotContain "## HEARTBEAT.md"
            result shouldNotContain "## BOOTSTRAP.md"
            result shouldNotContain "## MEMORY.md"
        }

        @Test
        fun `cron session produces minimal prompt`() {
            val ctx = fullContext().copy(
                sessionType = SessionType.CRON,
                promptMode = PromptMode.MINIMAL
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldNotContain "## HEARTBEAT.md"
            result shouldNotContain "## BOOTSTRAP.md"
            result shouldNotContain "## MEMORY.md"
        }

        @Test
        fun `group session gets full prompt but no MEMORY`() {
            val ctx = fullContext().copy(
                sessionType = SessionType.GROUP,
                promptMode = PromptMode.FULL,
                memory = "Secret memory"
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "## HEARTBEAT.md"
            result shouldContain "## BOOTSTRAP.md"
            result shouldNotContain "## MEMORY.md"
            result shouldNotContain "Secret memory"
        }
    }

    @Nested
    inner class WorkspaceTotalCap {

        @Test
        fun `workspace under total cap is unchanged`() {
            val soulContent = "A".repeat(100)
            val agentsContent = "B".repeat(100)
            val ctx = minimalContext().copy(
                soul = soulContent,
                agents = agentsContent,
                limits = PromptLimits(workspaceTotalCharLimit = 1_000_000)
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain soulContent
            result shouldContain agentsContent
            result shouldNotContain "content truncated"
        }

        @Test
        fun `workspace exceeding total cap gets largest file truncated`() {
            val soulContent = "A".repeat(300)
            val agentsContent = "B".repeat(400)
            val ctx = minimalContext().copy(
                soul = soulContent,
                agents = agentsContent,
                limits = PromptLimits(workspaceTotalCharLimit = 500)
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            result shouldContain "content truncated"
        }

        @Test
        fun `small files not truncated when large file is the culprit`() {
            val soulContent = "A".repeat(50)
            val agentsContent = "B".repeat(600)
            val ctx = minimalContext().copy(
                soul = soulContent,
                agents = agentsContent,
                limits = PromptLimits(workspaceTotalCharLimit = 500)
            )
            val result = PromptAssembler.assembleSystemPrompt(ctx)

            // The small soul file should appear unchanged
            result shouldContain soulContent
            // The large agents file should be truncated
            result shouldContain "content truncated"
        }
    }
}
