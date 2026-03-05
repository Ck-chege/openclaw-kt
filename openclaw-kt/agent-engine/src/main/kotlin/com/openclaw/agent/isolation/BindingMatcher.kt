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
     * Prefers explicit `channels` bindings on AgentEntry; falls back to
     * id-match only for entries with no explicit channel bindings.
     */
    fun matchByChannel(config: AgentsConfig, channel: String): AgentEntry? {
        // Prefer agents with explicit channel bindings
        val explicit = config.list.firstOrNull { it.channels.contains(channel) }
        if (explicit != null) {
            logger.debug { "Matched agent by channel binding: ${explicit.id} for channel=$channel" }
            return explicit
        }
        // Fall back to id-match only for entries without explicit channels
        val fallback = config.list.firstOrNull { it.channels.isEmpty() && it.id == channel }
        if (fallback != null) {
            logger.debug { "Matched agent by id fallback: ${fallback.id} for channel=$channel" }
        }
        return fallback
    }
}
