package com.kclaw.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import com.kclaw.protocol.identifiers.SessionKey
import com.kclaw.session.SessionMessage
import com.kclaw.session.SessionStore
import kotlinx.serialization.Serializable

/**
 * Retrieves conversation history for a given session.
 *
 * Returns formatted messages (user, assistant, tool calls, etc.)
 * with timestamps and role labels, limited to the most recent N messages.
 */
class SessionsHistoryTool(
    private val sessionStore: SessionStore
) : SimpleTool<SessionsHistoryTool.Args>(
    argsSerializer = Args.serializer(),
    name = "sessions_history",
    description = "Get conversation history for a session. Returns formatted messages with timestamps."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The session key to get history for")
        val key: String,
        @property:LLMDescription("Maximum number of messages to return (default: 50)")
        val limit: Int = 50
    )

    override suspend fun execute(args: Args): String {
        val sessionKey = SessionKey(args.key)
        val messages = sessionStore.getHistory(sessionKey)
        if (messages.isEmpty()) return "No messages found for session: ${args.key}"
        val limited = messages.takeLast(args.limit)
        return limited.joinToString("\n\n") { msg -> formatMessage(msg) }
    }

    private fun formatMessage(msg: SessionMessage): String = when (msg) {
        is SessionMessage.UserMessage -> "[${msg.timestamp}] USER (${msg.senderName}): ${msg.text}"
        is SessionMessage.AssistantMessage -> "[${msg.timestamp}] ASSISTANT: ${msg.text}"
        is SessionMessage.ToolCall -> "[${msg.timestamp}] TOOL_CALL: ${msg.toolName}(${msg.args})"
        is SessionMessage.ToolResult -> "[${msg.timestamp}] TOOL_RESULT: ${msg.toolName} -> ${msg.result} (${msg.durationMs}ms)"
        is SessionMessage.SystemEvent -> "[${msg.timestamp}] SYSTEM: ${msg.event}"
        is SessionMessage.CompactionSummary -> "[${msg.timestamp}] COMPACTION: ${msg.messagesCompacted} messages compacted"
    }
}
