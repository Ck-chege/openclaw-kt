package com.openclaw.agent.strategy

import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.dto.TokenUsage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Bridges Koog agent events to a Flow<AgentEvent> using a MutableSharedFlow.
 * Buffer capacity of 256 provides backpressure handling for fast producers.
 */
class StreamBridge {
    private val eventsChannel = Channel<AgentEvent>(capacity = 256)
    val events: Flow<AgentEvent> = eventsChannel.receiveAsFlow()

    // Approximate output token count: accumulate chars / 4 per delta.
    // Koog does not expose final token counts from agent.run() — this is the best available signal.
    private var outputTokenCount = 0

    fun currentOutputTokens(): Int = outputTokenCount

    fun onTokenDelta(text: String) {
        outputTokenCount += (text.length / 4).coerceAtLeast(1)
        logger.trace { "Token delta: ${text.take(50)}" }
        eventsChannel.trySend(AgentEvent.TokenDelta(text))
    }

    fun onToolCallStart(name: String, args: JsonObject) {
        logger.debug { "Tool call start: $name" }
        eventsChannel.trySend(AgentEvent.ToolCallStart(name, args))
    }

    fun onToolCallResult(name: String, result: String, durationMs: Long) {
        logger.debug { "Tool call result: $name (${durationMs}ms)" }
        eventsChannel.trySend(AgentEvent.ToolCallResult(name, result, durationMs))
    }

    fun onRunComplete(finalText: String, usage: TokenUsage) {
        logger.info { "Run complete: ${finalText.take(100)}" }
        eventsChannel.trySend(AgentEvent.RunComplete(finalText, usage))
    }

    fun onRunError(message: String, code: String? = null) {
        logger.error { "Run error: $message (code=$code)" }
        eventsChannel.trySend(AgentEvent.RunError(message, code))
    }

    fun close() {
        eventsChannel.close()
    }
}
