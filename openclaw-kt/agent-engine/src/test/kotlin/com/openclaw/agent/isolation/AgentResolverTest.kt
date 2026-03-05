package com.openclaw.agent.isolation

import com.openclaw.gateway.config.AgentEntry
import com.openclaw.gateway.config.AgentsConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AgentResolverTest {

    private val resolver = AgentResolver()

    @Nested
    inner class DefaultResolution {

        @Test
        fun `default config resolves to default agent`() {
            val config = AgentsConfig()
            val agent = resolver.resolve(config)
            agent.agentId.raw shouldBe "default"
            agent.modelId.raw shouldBe "anthropic:claude-sonnet-4"
            agent.toolProfile shouldBe "full"
        }

        @Test
        fun `default config workspace path is expanded`() {
            val config = AgentsConfig()
            val agent = resolver.resolve(config)
            // Should NOT contain ~ (tilde should be expanded)
            agent.workspace.workspacePath.toString() shouldNotContain "~"
        }
    }

    @Nested
    inner class AgentOverrides {

        @Test
        fun `agent in list with matching id resolves with overrides`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "custom", model = "openai:gpt-4o", workspace = "/tmp/custom"))
            )
            val agent = resolver.resolve(config, "custom")
            agent.agentId.raw shouldBe "custom"
            agent.modelId.raw shouldBe "openai:gpt-4o"
        }

        @Test
        fun `agent override for model uses overridden model defaults for rest`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "partial", model = "openai:gpt-4o"))
            )
            val agent = resolver.resolve(config, "partial")
            agent.modelId.raw shouldBe "openai:gpt-4o"
            // workspace comes from defaults — should be expanded home path, not a blank value
            agent.workspace shouldNotBe null
        }

        @Test
        fun `agent override for workspace creates loader at override path`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "ws-agent", workspace = "/tmp/test-workspace"))
            )
            val agent = resolver.resolve(config, "ws-agent")
            agent.workspace.workspacePath.toString() shouldBe "/tmp/test-workspace"
        }

        @Test
        fun `unknown agent id resolves to defaults`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "known"))
            )
            val agent = resolver.resolve(config, "unknown")
            agent.modelId.raw shouldBe "anthropic:claude-sonnet-4"
        }
    }

    @Nested
    inner class ChannelResolution {

        @Test
        fun `resolveForChannel with matching channel entry uses it`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "telegram", model = "openai:gpt-4o"))
            )
            val agent = resolver.resolveForChannel(config, "telegram")
            agent.modelId.raw shouldBe "openai:gpt-4o"
        }

        @Test
        fun `resolveForChannel with no match uses defaults`() {
            val config = AgentsConfig()
            val agent = resolver.resolveForChannel(config, "unknown-channel")
            agent.modelId.raw shouldBe "anthropic:claude-sonnet-4"
        }
    }
}
