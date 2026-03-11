package com.kclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.kclaw.session.SessionStore
import kotlinx.serialization.Serializable

/**
 * Lists all active sessions with their metadata.
 *
 * Returns a formatted summary including session key, message count,
 * model, and compaction count for each session.
 */
class SessionsListTool(
    private val sessionStore: SessionStore
) : SimpleTool<SessionsListTool.Args>(
    argsSerializer = Args.serializer(),
    name = "sessions_list",
    description = "List all active sessions with their metadata (agent, channel, message count, last activity)."
) {
    @Serializable
    class Args

    override suspend fun execute(args: Args): String {
        val sessions = sessionStore.listSessions()
        if (sessions.isEmpty()) return "No active sessions"
        return sessions.joinToString("\n") { meta ->
            "${meta.key.raw}  agent=${meta.agentId}  messages=${meta.messageCount}  model=${meta.modelId ?: "default"}  compactions=${meta.compactionCount}"
        }
    }
}
