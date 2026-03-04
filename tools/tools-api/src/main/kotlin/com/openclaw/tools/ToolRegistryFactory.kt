package com.openclaw.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import com.openclaw.gateway.config.AgentToolOverrides
import com.openclaw.gateway.config.ToolsConfig
import com.openclaw.tools.policy.CallerContext
import com.openclaw.tools.policy.ToolPolicyPipeline
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
        callerContext: CallerContext = CallerContext()
    ): ToolRegistry {
        val allToolNames = allTools.map { it.name }.toSet()
        val allowedNames = policyPipeline.filterAllowed(
            allToolNames, toolsConfig, agentToolOverrides, callerContext
        )

        if (allowedNames.isEmpty()) {
            logger.info { "No tools allowed for agent=${callerContext.agentId}, returning empty registry" }
            return ToolRegistry.EMPTY
        }

        val allowedTools = allTools.filter { it.name in allowedNames }
        logger.info { "Built tool registry for agent=${callerContext.agentId}: ${allowedTools.map { it.name }}" }

        return ToolRegistry {
            for (tool in allowedTools) {
                tool(tool)
            }
        }
    }
}
