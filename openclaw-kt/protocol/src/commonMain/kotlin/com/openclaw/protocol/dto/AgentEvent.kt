package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class AgentEvent {
    @Serializable
    data class TokenDelta(val text: String) : AgentEvent()

    @Serializable
    data class ToolCallStart(val name: String, val args: JsonObject) : AgentEvent()

    @Serializable
    data class ToolCallResult(val name: String, val result: String, val durationMs: Long) : AgentEvent()

    @Serializable
    data class RunComplete(val finalText: String, val usage: TokenUsage) : AgentEvent()

    @Serializable
    data class RunError(val message: String, val code: String? = null) : AgentEvent()
}

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)
