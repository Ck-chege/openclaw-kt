package com.kclaw.agent.tools

import ai.koog.agents.core.tools.Tool
import com.kclaw.agent.runtime.RunManager
import com.kclaw.gateway.config.AgentsConfig
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionStore
import com.kclaw.skills.SkillLoader
import java.nio.file.Path

/**
 * Factory that creates all KClaw-native tool instances.
 *
 * These tools provide introspective capabilities (session listing, history,
 * cross-session messaging, status, agent config) that are unique to KClaw
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
    private val workspacePath: () -> Path = { Path.of(".") },
    private val skillLoader: SkillLoader? = null
) {
    /**
     * Returns all native tools as a list. Each call creates fresh instances
     * so they can be scoped per-session if needed (e.g., different currentSessionKey).
     */
    fun allTools(): List<Tool<*, *>> = buildList {
        add(SessionsListTool(sessionStore))
        add(SessionsHistoryTool(sessionStore))
        add(SessionsSendTool(runManager))
        add(SessionStatusTool(sessionStore, currentSessionKey))
        add(AgentsListTool(agentsConfig))
        add(CreateSkillTool(workspacePath))
        if (skillLoader != null) {
            add(UseSkillTool(skillLoader, workspacePath))
        }
    }
}
