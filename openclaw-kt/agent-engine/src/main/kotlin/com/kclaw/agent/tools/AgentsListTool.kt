package com.kclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.kclaw.gateway.config.AgentsConfig
import kotlinx.serialization.Serializable

/**
 * Lists all configured agents including defaults and per-agent overrides.
 *
 * The config is provided via a lambda to support hot-reload --
 * each invocation reads the latest configuration.
 */
class AgentsListTool(
    private val agentsConfig: () -> AgentsConfig
) : SimpleTool<AgentsListTool.Args>(
    argsSerializer = Args.serializer(),
    name = "agents_list",
    description = "List all configured agents with their model, workspace, and tool overrides."
) {
    @Serializable
    class Args

    override suspend fun execute(args: Args): String {
        val config = agentsConfig()
        return buildString {
            appendLine("Default: model=${config.defaults.model}, workspace=${config.defaults.workspace}")
            if (config.list.isEmpty()) {
                append("No additional agents configured")
            } else {
                config.list.forEachIndexed { index, entry ->
                    if (index > 0) appendLine()
                    append("Agent '${entry.id}': model=${entry.model ?: "default"}, workspace=${entry.workspace ?: "default"}")
                }
            }
        }
    }
}
