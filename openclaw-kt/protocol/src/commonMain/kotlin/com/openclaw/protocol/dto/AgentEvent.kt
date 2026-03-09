package com.openclaw.protocol.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Events emitted during an agent run, streamed to clients over WebSocket.
 * Each subtype represents a distinct phase of the agent's execution lifecycle.
 */
@Serializable
sealed class AgentEvent {
    /** A chunk of streamed text from the LLM response, delivered as it's generated. */
    @Serializable
    data class TokenDelta(val text: String) : AgentEvent()

    /** The agent is invoking a tool. Emitted before execution with the tool name and arguments. */
    @Serializable
    data class ToolCallStart(val name: String, val args: JsonObject) : AgentEvent()

    /** A tool call has finished. Contains the tool's output and how long it took. */
    @Serializable
    data class ToolCallResult(val name: String, val result: String, val durationMs: Long) : AgentEvent()

    /** The agent run completed successfully. Contains the final assembled response and token usage. */
    @Serializable
    data class RunComplete(val finalText: String, val usage: TokenUsage) : AgentEvent()

    /** The agent run failed. Contains an error message and optional error code for categorization. */
    @Serializable
    data class RunError(val message: String, val code: String? = null) : AgentEvent()
}

/** Token consumption for a single agent run. */
@Serializable
data class TokenUsage(
    /** Number of tokens in the prompt (system + history + user message). Currently estimated. */
    val promptTokens: Int = 0,
    /** Number of tokens in the LLM's completion output. */
    val completionTokens: Int = 0,
    /** Sum of prompt + completion tokens. */
    val totalTokens: Int = 0
)
