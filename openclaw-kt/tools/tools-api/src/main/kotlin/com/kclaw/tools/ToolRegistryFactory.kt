package com.kclaw.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import com.kclaw.gateway.config.AgentToolOverrides
import com.kclaw.gateway.config.ToolsConfig
import com.kclaw.tools.policy.CallerContext
import com.kclaw.tools.policy.ToolPolicyPipeline
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Builds a per-agent Koog [ToolRegistry] by collecting all registered tools,
 * running them through the [ToolPolicyPipeline], and returning only permitted ones.
 *
 * Policy filtering happens BEFORE registry creation (not at execution time),
 * because Koog's `nodeExecuteTool()` directly invokes tools from the registry
 * with no interception point.
 */
class ToolRegistryFactory(
    private val policyPipeline: ToolPolicyPipeline,
    private val allTools: List<Tool<*, *>>
) {
    /**
     * Build a filtered [ToolRegistry] for a specific agent context.
     * Only tools that pass all 6 policy stages are included.
     */
    fun buildRegistry(
        toolsConfig: ToolsConfig,
        agentToolOverrides: AgentToolOverrides? = null,
        callerContext: CallerContext,
        extraTools: List<Tool<*, *>> = emptyList()
    ): ToolRegistry {
        val allowedTools = filterAllowedTools(toolsConfig, agentToolOverrides, callerContext, extraTools)

        if (allowedTools.isEmpty()) {
            return ToolRegistry.EMPTY
        }

        return ToolRegistry {
            for (tool in allowedTools) {
                tool(tool)
            }
        }
    }

    /**
     * Get name + description pairs for tools allowed by the policy pipeline.
     * Used to inject tool descriptions into the system prompt (Zone 1).
     */
    fun buildDescriptions(
        toolsConfig: ToolsConfig,
        agentToolOverrides: AgentToolOverrides? = null,
        callerContext: CallerContext,
        extraTools: List<Tool<*, *>> = emptyList()
    ): List<Pair<String, String>> {
        return filterAllowedTools(toolsConfig, agentToolOverrides, callerContext, extraTools)
            .map { it.name to it.descriptor.description }
    }

    private fun filterAllowedTools(
        toolsConfig: ToolsConfig,
        agentToolOverrides: AgentToolOverrides?,
        callerContext: CallerContext,
        extraTools: List<Tool<*, *>> = emptyList()
    ): List<Tool<*, *>> {
        val availableTools = buildList {
            addAll(allTools)
            addAll(extraTools)
        }
        val allToolNames = availableTools.map { it.name }.toSet()
        val allowedNames = policyPipeline.filterAllowed(
            allToolNames, toolsConfig, agentToolOverrides, callerContext
        )

        if (allowedNames.isEmpty()) {
            logger.info { "No tools allowed for agent=${callerContext.agentId}, returning empty" }
            return emptyList()
        }

        val allowedTools = LinkedHashMap<String, Tool<*, *>>().apply {
            availableTools.forEach { tool ->
                if (tool.name in allowedNames) {
                    this[tool.name] = tool
                }
            }
        }.values.toList()
        logger.info { "Filtered tools for agent=${callerContext.agentId}: ${allowedTools.map { it.name }}" }
        return allowedTools
    }
}
