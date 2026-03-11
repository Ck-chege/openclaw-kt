package com.kclaw.agent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall

/**
 * Builds the KClaw agent strategy graph using Koog's strategy DSL.
 *
 * Flow: start → sendToLLM → [assistant → finish | tool call → executeTool
 *       → sendToolResult → [assistant → finish | tool call → executeTool → ...]]
 */
fun buildKClawStrategy() =
    strategy<String, String>("kclaw-agent") {
        val sendToLLM by nodeLLMRequest()
        val executeTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        // Initial LLM request
        edge(nodeStart forwardTo sendToLLM)

        // LLM response routing
        edge(sendToLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToLLM forwardTo executeTool onToolCall { true })

        // After tool execution: send the tool result back to the LLM.
        edge(executeTool forwardTo sendToolResult)

        // After sending tool result: LLM responds — route again
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(sendToolResult forwardTo executeTool onToolCall { true })
    }
