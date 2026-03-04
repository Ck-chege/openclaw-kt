package com.openclaw.agent.isolation

import com.openclaw.gateway.config.AgentEntry
import com.openclaw.gateway.config.AgentsConfig
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class BindingMatcher {
    /**
     * Find the first agent entry matching the given agentId from the config list.
     * Returns null if no match found (caller should use defaults).
     */
    fun match(config: AgentsConfig, agentId: String): AgentEntry? {
        val entry = config.list.firstOrNull { it.id == agentId }
        if (entry != null) {
            logger.debug { "Matched agent binding: ${entry.id}" }
        }
        return entry
    }

    /**
     * Find the first agent entry matching by channel name.
     * This is for channel-based routing where the agent config list
     * might have entries intended for specific channels.
     */
    fun matchByChannel(config: AgentsConfig, channel: String): AgentEntry? {
        // For now, we support matching by agent id that equals the channel name
        // Future: add explicit channel bindings to AgentEntry
        return config.list.firstOrNull { it.id == channel }
    }
}
