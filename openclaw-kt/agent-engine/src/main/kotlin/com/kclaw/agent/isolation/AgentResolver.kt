package com.kclaw.agent.isolation

import com.kclaw.agent.workspace.WorkspaceLoader
import com.kclaw.gateway.config.AgentsConfig
import com.kclaw.protocol.identifiers.AgentId
import com.kclaw.protocol.identifiers.ModelId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

class AgentResolver(
    private val bindingMatcher: BindingMatcher = BindingMatcher()
) {
    /**
     * Resolve an agent by its ID from config, applying defaults for missing overrides.
     */
    fun resolve(config: AgentsConfig, agentId: String? = null): ResolvedAgent {
        val defaults = config.defaults
        val entry = if (agentId != null) bindingMatcher.match(config, agentId) else null

        val resolvedModelId = ModelId(entry?.model ?: defaults.model)
        val resolvedWorkspacePath = expandPath(entry?.workspace ?: defaults.workspace)
        val resolvedToolProfile = entry?.tools?.profile ?: "full"
        val resolvedToolOverrides = entry?.tools

        logger.info { "Resolved agent: id=${agentId ?: "default"}, model=${resolvedModelId.raw}, workspace=$resolvedWorkspacePath, toolProfile=$resolvedToolProfile" }

        return ResolvedAgent(
            agentId = AgentId(agentId ?: "default"),
            modelId = resolvedModelId,
            workspace = WorkspaceLoader(resolvedWorkspacePath),
            toolProfile = resolvedToolProfile,
            toolOverrides = resolvedToolOverrides
        )
    }

    /**
     * Resolve an agent for a specific channel context.
     */
    fun resolveForChannel(config: AgentsConfig, channel: String): ResolvedAgent {
        val entry = bindingMatcher.matchByChannel(config, channel)
        return if (entry != null) {
            resolve(config, entry.id)
        } else {
            resolve(config)
        }
    }

    private fun expandPath(path: String): Path {
        val expanded = if (path.startsWith("~")) {
            path.replaceFirst("~", System.getProperty("user.home"))
        } else {
            path
        }
        return Path.of(expanded)
    }
}
