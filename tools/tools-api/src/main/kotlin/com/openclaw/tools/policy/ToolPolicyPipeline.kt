package com.openclaw.tools.policy

import com.openclaw.gateway.config.AgentToolOverrides
import com.openclaw.gateway.config.SandboxToolConfig
import com.openclaw.gateway.config.ToolsConfig
import com.openclaw.protocol.dto.ToolPermission
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 6-stage cascading tool policy pipeline.
 * Each stage can only narrow (never widen) the set of allowed tools.
 *
 * Stages:
 * 1. Profile — base allowlist (full, safe, minimal, none)
 * 2. Global allow/deny — explicit overrides from tools.allow + tools.deny
 * 3. Per-provider — restrictions from tools.byProvider[key]
 * 4. Per-agent — overrides from agents.list[i].tools
 * 5. Sandbox — additional filtering when sandboxed
 * 6. Owner gate — owner-only tools require caller.isOwner
 *
 * Matches OpenClaw's applyToolPolicyPipeline() with stages consolidated
 * per SPEC.md's 6-stage design.
 */
class ToolPolicyPipeline {

    /**
     * Evaluate whether a specific tool is permitted for the given context.
     */
    fun evaluate(
        toolName: String,
        toolsConfig: ToolsConfig,
        agentToolOverrides: AgentToolOverrides? = null,
        callerContext: CallerContext = CallerContext()
    ): ToolPermission {
        // Stage 1: Profile
        val profile = ToolProfile.fromString(
            agentToolOverrides?.profile ?: toolsConfig.profile
        )
        if (!profile.allowsAll && toolName !in profile.allowedTools) {
            logger.debug { "Tool '$toolName' denied by profile '${profile.name}'" }
            return ToolPermission.Denied("Tool '$toolName' not allowed by profile '${profile.name.lowercase()}'")
        }

        // Stage 2: Global allow/deny
        val expandedAllow = ToolGroup.expandAll(toolsConfig.allow)
        val expandedDeny = ToolGroup.expandAll(toolsConfig.deny)

        if (expandedDeny.isNotEmpty() && toolName in expandedDeny) {
            logger.debug { "Tool '$toolName' denied by global deny list" }
            return ToolPermission.Denied("Tool '$toolName' is in the global deny list")
        }
        if (expandedAllow.isNotEmpty() && toolName !in expandedAllow) {
            logger.debug { "Tool '$toolName' denied — not in global allow list" }
            return ToolPermission.Denied("Tool '$toolName' not in global allow list")
        }

        // Stage 3: Per-provider restrictions
        val providerName = callerContext.provider
        if (providerName != null) {
            val providerConfig = toolsConfig.byProvider[providerName]
            if (providerConfig != null) {
                val providerDeny = ToolGroup.expandAll(providerConfig.deny)
                val providerAllow = ToolGroup.expandAll(providerConfig.allow)

                if (providerDeny.isNotEmpty() && toolName in providerDeny) {
                    logger.debug { "Tool '$toolName' denied by provider '$providerName' deny list" }
                    return ToolPermission.Denied("Tool '$toolName' denied for provider '$providerName'")
                }
                if (providerAllow.isNotEmpty() && toolName !in providerAllow) {
                    logger.debug { "Tool '$toolName' denied — not in provider '$providerName' allow list" }
                    return ToolPermission.Denied("Tool '$toolName' not in provider '$providerName' allow list")
                }
            }
        }

        // Stage 4: Per-agent overrides
        if (agentToolOverrides != null) {
            val agentDeny = ToolGroup.expandAll(agentToolOverrides.deny)
            val agentAllow = ToolGroup.expandAll(agentToolOverrides.allow)

            if (agentDeny.isNotEmpty() && toolName in agentDeny) {
                logger.debug { "Tool '$toolName' denied by agent override deny list" }
                return ToolPermission.Denied("Tool '$toolName' denied by agent configuration")
            }
            if (agentAllow.isNotEmpty() && toolName !in agentAllow) {
                logger.debug { "Tool '$toolName' denied — not in agent override allow list" }
                return ToolPermission.Denied("Tool '$toolName' not in agent allow list")
            }
        }

        // Stage 5: Sandbox restrictions
        val sandbox = toolsConfig.sandbox
        if (sandbox.enabled) {
            val sandboxDeny = ToolGroup.expandAll(sandbox.deny)
            val sandboxAllow = ToolGroup.expandAll(sandbox.allow)

            if (sandboxDeny.isNotEmpty() && toolName in sandboxDeny) {
                logger.debug { "Tool '$toolName' denied by sandbox deny list" }
                return ToolPermission.Denied("Tool '$toolName' denied in sandbox mode")
            }
            if (sandboxAllow.isNotEmpty() && toolName !in sandboxAllow) {
                logger.debug { "Tool '$toolName' denied — not in sandbox allow list" }
                return ToolPermission.Denied("Tool '$toolName' not allowed in sandbox mode")
            }
        }

        // Stage 6: Owner-only gate
        if (toolName in DangerousTools.OWNER_ONLY && !callerContext.isOwner) {
            logger.debug { "Tool '$toolName' denied — owner-only, caller is not owner" }
            return ToolPermission.Denied("Tool '$toolName' requires owner access")
        }

        return ToolPermission.Allowed
    }

    /**
     * Filter a set of tool names down to only those allowed by the pipeline.
     * Used to build a per-agent ToolRegistry before the agent run starts.
     */
    fun filterAllowed(
        allToolNames: Set<String>,
        toolsConfig: ToolsConfig,
        agentToolOverrides: AgentToolOverrides? = null,
        callerContext: CallerContext = CallerContext()
    ): Set<String> = allToolNames.filter { toolName ->
        evaluate(toolName, toolsConfig, agentToolOverrides, callerContext) is ToolPermission.Allowed
    }.toSet()
}
