package com.openclaw.agent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult

/**
 * Builds the OpenClaw agent strategy graph using Koog's strategy DSL.
 *
 * Flow: start → sendToLLM → [assistant → finish | tool call → executeTool
 *       → (compressHistory if over threshold) → sendToolResult
 *       → [assistant → finish | tool call → executeTool → ...]]
 *
 * @param compactionThreshold Number of messages before triggering history compression
 */
fun buildOpenClawStrategy(compactionThreshold: Int = 100) =
    strategy<String, String>("openclaw-agent") {
        val sendToLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()
        val compressHistory by nodeLLMCompressHistory<ReceivedToolResult>()

        // Initial LLM request
        edge(nodeStart forwardTo sendToLLM)

        // LLM response routing
        edge(sendToLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToLLM forwardTo executeTool onToolCall { true })

        // After tool execution: compress if over threshold, then send result
        edge(
            (executeTool forwardTo compressHistory).onCondition { _ ->
                llm.readSession { prompt.messages.size > compactionThreshold }
            }
        )
        edge(compressHistory forwardTo sendToolResult)
        edge(
            (executeTool forwardTo sendToolResult).onCondition { _ ->
                llm.readSession { prompt.messages.size <= compactionThreshold }
            }
        )

        // After sending tool result: LLM responds — route again
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToolResult forwardTo executeTool onToolCall { true })
    }
