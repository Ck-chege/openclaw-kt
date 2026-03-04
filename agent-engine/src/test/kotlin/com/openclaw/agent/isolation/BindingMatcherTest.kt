package com.openclaw.agent.isolation

import com.openclaw.gateway.config.AgentEntry
import com.openclaw.gateway.config.AgentsConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BindingMatcherTest {

    private val matcher = BindingMatcher()

    @Nested
    inner class MatchById {

        @Test
        fun `no bindings configured returns null`() {
            val config = AgentsConfig()
            matcher.match(config, "any") shouldBe null
        }

        @Test
        fun `binding matches specific agent by id`() {
            val config = AgentsConfig(
                list = listOf(
                    AgentEntry(id = "agent-a", model = "openai:gpt-4o"),
                    AgentEntry(id = "agent-b")
                )
            )
            val entry = matcher.match(config, "agent-a")
            entry shouldNotBe null
            entry!!.model shouldBe "openai:gpt-4o"
        }

        @Test
        fun `multiple bindings first match wins`() {
            val config = AgentsConfig(
                list = listOf(
                    AgentEntry(id = "first", model = "openai:gpt-4o"),
                    AgentEntry(id = "first", model = "anthropic:claude-sonnet-4-5")
                )
            )
            val entry = matcher.match(config, "first")
            entry!!.model shouldBe "openai:gpt-4o"
        }

        @Test
        fun `no match returns null`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "agent-a"))
            )
            matcher.match(config, "agent-b") shouldBe null
        }
    }

    @Nested
    inner class MatchByChannel {

        @Test
        fun `matchByChannel finds entry by channel name`() {
            val config = AgentsConfig(
                list = listOf(AgentEntry(id = "telegram", model = "openai:gpt-4o"))
            )
            val entry = matcher.matchByChannel(config, "telegram")
            entry shouldNotBe null
            entry!!.id shouldBe "telegram"
        }

        @Test
        fun `matchByChannel returns null when no match`() {
            val config = AgentsConfig()
            matcher.matchByChannel(config, "discord") shouldBe null
        }
    }
}
