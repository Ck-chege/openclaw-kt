package com.kclaw.session

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class SessionMessage {
    abstract val id: String
    abstract val timestamp: Instant

    @Serializable
    @SerialName("user")
    data class UserMessage(
        override val id: String,
        override val timestamp: Instant,
        val senderId: String,
        val senderName: String,
        val text: String,
        val channel: String? = null
    ) : SessionMessage()

    @Serializable
    @SerialName("assistant")
    data class AssistantMessage(
        override val id: String,
        override val timestamp: Instant,
        val text: String,
        val model: String? = null,
        val usage: com.kclaw.protocol.dto.TokenUsage? = null
    ) : SessionMessage()

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        override val id: String,
        override val timestamp: Instant,
        val toolName: String,
        val args: JsonObject,
        val requestId: String
    ) : SessionMessage()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        override val id: String,
        override val timestamp: Instant,
        val toolName: String,
        val result: String,
        val requestId: String,
        val durationMs: Long
    ) : SessionMessage()

    @Serializable
    @SerialName("system_event")
    data class SystemEvent(
        override val id: String,
        override val timestamp: Instant,
        val event: String,
        val data: JsonObject? = null
    ) : SessionMessage()

    @Serializable
    @SerialName("compaction")
    data class CompactionSummary(
        override val id: String,
        override val timestamp: Instant,
        val summary: String,
        val messagesCompacted: Int,
        val tokensBefore: Int,
        val tokensAfter: Int
    ) : SessionMessage()
}
