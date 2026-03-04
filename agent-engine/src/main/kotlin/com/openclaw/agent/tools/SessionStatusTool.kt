package com.openclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionStore
import kotlinx.serialization.Serializable

/**
 * Reports metadata for the current session.
 *
 * Returns session key, message count, model, compaction count,
 * and total token usage. The current session key is provided
 * via a lambda at construction time.
 */
class SessionStatusTool(
    private val sessionStore: SessionStore,
    private val currentSessionKey: () -> SessionKey
) : SimpleTool<SessionStatusTool.Args>(
    argsSerializer = Args.serializer(),
    name = "session_status",
    description = "Get metadata for the current session (message count, model, tokens, compaction count)."
) {
    @Serializable
    class Args

    override suspend fun execute(args: Args): String {
        val key = currentSessionKey()
        val meta = sessionStore.getSession(key)
            ?: return "No session metadata available"
        return buildString {
            appendLine("Session: ${key.raw}")
            appendLine("Agent: ${meta.agentId}")
            appendLine("Messages: ${meta.messageCount}")
            appendLine("Model: ${meta.modelId ?: "default"}")
            appendLine("Compactions: ${meta.compactionCount}")
            append("Total tokens: ${meta.totalTokens}")
        }
    }
}
