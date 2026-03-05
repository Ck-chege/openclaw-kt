package com.openclaw.agent.runtime

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import com.openclaw.agent.model.ModelRegistry
import com.openclaw.agent.strategy.buildOpenClawStrategy
import com.openclaw.protocol.identifiers.ModelId
import com.openclaw.protocol.identifiers.SessionKey
import com.openclaw.session.SessionMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Outcome of a pre-compaction memory flush attempt.
 */
sealed class FlushResult {
    /** Agent wrote memories to disk. */
    data class Flushed(val text: String) : FlushResult()

    /** Agent replied NO_REPLY — nothing to store. */
    data object Skipped : FlushResult()

    /** Flush failed with an exception. */
    data class Failed(val cause: Throwable) : FlushResult()
}

/**
 * Runs a silent agentic turn before compaction to persist important memories.
 *
 * The agent receives the conversation history as context and the SPEC §11.3
 * flush prompt, then gets a WriteTool-only registry to write `memory/YYYY-MM-DD.md`.
 * No events are emitted to WebSocket clients (silent turn).
 */
open class MemoryFlushRunner(
    private val modelRegistry: ModelRegistry,
    private val toolRegistry: ToolRegistry,
    private val flushModelId: ModelId = ModelId("anthropic:claude-sonnet-4")
) {

    /**
     * Run a silent memory-flush turn.
     *
     * @param sessionKey Session being compacted
     * @param history Current conversation history (pre-compaction)
     * @return [FlushResult] indicating what happened
     */
    open suspend fun runFlush(
        sessionKey: SessionKey,
        history: List<SessionMessage>
    ): FlushResult {
        return try {
            logger.info { "Starting memory flush for session: ${sessionKey.raw}" }

            val systemPrompt = buildFlushPrompt()
            val userMessage = buildContextSummary(history)

            val executor = modelRegistry.resolveExecutor(flushModelId)
            val definition = modelRegistry.resolve(flushModelId)
            val koogModel = modelRegistry.resolveKoogModel(definition)

            val strategy = buildOpenClawStrategy()
            val agentConfig = AIAgentConfig(
                prompt = prompt("memory-flush-${sessionKey.raw}") {
                    system(systemPrompt)
                    user(userMessage)
                },
                model = koogModel,
                maxAgentIterations = 3
            )

            val agent = AIAgent(
                promptExecutor = executor,
                agentConfig = agentConfig,
                strategy = strategy,
                toolRegistry = toolRegistry
            )

            val result = agent.run(userMessage)

            if (isNoReply(result)) {
                logger.info { "Memory flush: nothing to store (NO_REPLY)" }
                FlushResult.Skipped
            } else {
                logger.info { "Memory flush completed for session: ${sessionKey.raw}" }
                FlushResult.Flushed(result)
            }
        } catch (e: Exception) {
            logger.error(e) { "Memory flush failed for session: ${sessionKey.raw}" }
            FlushResult.Failed(e)
        }
    }

    internal fun buildFlushPrompt(): String {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString() // YYYY-MM-DD

        return """
            |Pre-compaction memory flush. Store durable memories now.
            |Use memory/$today.md. Create the memory/ directory if needed.
            |IMPORTANT: If the file already exists, APPEND new content only.
            |If nothing to store, reply with NO_REPLY.
        """.trimMargin()
    }

    internal fun buildContextSummary(history: List<SessionMessage>): String {
        if (history.isEmpty()) return "No conversation history."

        val sb = StringBuilder("Recent conversation context:\n\n")
        // Include last 20 messages to give the agent enough context
        val recent = history.takeLast(20)
        for (msg in recent) {
            when (msg) {
                is SessionMessage.UserMessage -> sb.appendLine("User: ${msg.text}")
                is SessionMessage.AssistantMessage -> sb.appendLine("Assistant: ${msg.text}")
                is SessionMessage.CompactionSummary -> sb.appendLine("[Prior context summary]: ${msg.summary}")
                is SessionMessage.ToolCall -> sb.appendLine("[Tool call: ${msg.toolName}]")
                is SessionMessage.ToolResult -> sb.appendLine("[Tool result: ${msg.result.take(200)}]")
                is SessionMessage.SystemEvent -> {}
            }
        }
        return sb.toString()
    }

    internal fun isNoReply(response: String): Boolean {
        val trimmed = response.trim()
        return trimmed.isEmpty() || trimmed.equals("NO_REPLY", ignoreCase = true)
    }
}
