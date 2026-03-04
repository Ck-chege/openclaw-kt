package com.openclaw.agent.tools

import com.openclaw.gateway.config.AgentDefaults
import com.openclaw.gateway.config.AgentEntry
import com.openclaw.gateway.config.AgentsConfig
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AgentsListToolTest {

    @Nested
    inner class Execute {

        @Test
        fun `returns formatted agent list with defaults`() = runTest {
            val config = AgentsConfig(
                defaults = AgentDefaults(
                    model = "anthropic:claude-sonnet-4-5",
                    workspace = "~/.openclawk/workspace"
                )
            )
            val tool = AgentsListTool { config }

            val result = tool.execute(AgentsListTool.Args())

            result shouldContain "Default: model=anthropic:claude-sonnet-4-5, workspace=~/.openclawk/workspace"
            result shouldContain "No additional agents configured"
        }

        @Test
        fun `includes per-agent overrides in output`() = runTest {
            val config = AgentsConfig(
                defaults = AgentDefaults(),
                list = listOf(
                    AgentEntry(id = "trading", model = "openai:gpt-4o", workspace = "/opt/trading"),
                    AgentEntry(id = "helper", model = null, workspace = null)
                )
            )
            val tool = AgentsListTool { config }

            val result = tool.execute(AgentsListTool.Args())

            result shouldContain "Agent 'trading': model=openai:gpt-4o, workspace=/opt/trading"
            result shouldContain "Agent 'helper': model=default, workspace=default"
        }

        @Test
        fun `reads latest config on each invocation`() = runTest {
            var config = AgentsConfig()
            val tool = AgentsListTool { config }

            val first = tool.execute(AgentsListTool.Args())
            first shouldContain "No additional agents configured"

            // Update config
            config = AgentsConfig(list = listOf(AgentEntry(id = "new-agent")))
            val second = tool.execute(AgentsListTool.Args())

            second shouldContain "Agent 'new-agent'"
        }
    }
}
