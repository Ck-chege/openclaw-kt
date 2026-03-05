package com.openclaw.agent.tools

import ai.koog.agents.core.tools.Tool
import com.openclaw.agent.runtime.RunManager
import com.openclaw.gateway.config.AgentsConfig
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionStore
import java.nio.file.Path

/**
 * Factory that creates all OpenClaw-native tool instances.
 *
 * These tools provide introspective capabilities (session listing, history,
 * cross-session messaging, status, agent config) that are unique to OpenClaw
 * and not provided by external tool providers.
 *
 * Each tool is a class instance (not an object singleton) because it requires
 * injected dependencies like SessionStore, RunManager, and config providers.
 */
class NativeToolsProvider(
    private val sessionStore: SessionStore,
    private val runManager: RunManager,
    private val agentsConfig: () -> AgentsConfig,
    private val currentSessionKey: () -> SessionKey,
    private val workspacePath: () -> Path = { Path.of(".") }
) {
    /**
     * Returns all native tools as a list. Each call creates fresh instances
     * so they can be scoped per-session if needed (e.g., different currentSessionKey).
     */
    fun allTools(): List<Tool<*, *>> = listOf(
        SessionsListTool(sessionStore),
        SessionsHistoryTool(sessionStore),
        SessionsSendTool(runManager),
        SessionStatusTool(sessionStore, currentSessionKey),
        AgentsListTool(agentsConfig),
        CreateSkillTool(workspacePath)
    )
}
