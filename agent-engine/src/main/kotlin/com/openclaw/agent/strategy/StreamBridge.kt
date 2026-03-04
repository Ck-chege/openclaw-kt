package com.openclaw.agent.strategy

import com.openclaw.protocol.dto.AgentEvent
import com.openclaw.protocol.dto.TokenUsage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Bridges Koog agent events to a Flow<AgentEvent> using a MutableSharedFlow.
 * Buffer capacity of 256 provides backpressure handling for fast producers.
 */
class StreamBridge {
    private val _events = MutableSharedFlow<AgentEvent>(
        extraBufferCapacity = 256
    )
    val events: Flow<AgentEvent> = _events.asSharedFlow()

    fun onTokenDelta(text: String) {
        logger.trace { "Token delta: ${text.take(50)}" }
        _events.tryEmit(AgentEvent.TokenDelta(text))
    }

    fun onToolCallStart(name: String, args: JsonObject) {
        logger.debug { "Tool call start: $name" }
        _events.tryEmit(AgentEvent.ToolCallStart(name, args))
    }

    fun onToolCallResult(name: String, result: String, durationMs: Long) {
        logger.debug { "Tool call result: $name (${durationMs}ms)" }
        _events.tryEmit(AgentEvent.ToolCallResult(name, result, durationMs))
    }

    fun onRunComplete(finalText: String, usage: TokenUsage) {
        logger.info { "Run complete: ${finalText.take(100)}" }
        _events.tryEmit(AgentEvent.RunComplete(finalText, usage))
    }

    fun onRunError(message: String, code: String? = null) {
        logger.error { "Run error: $message (code=$code)" }
        _events.tryEmit(AgentEvent.RunError(message, code))
    }
}
