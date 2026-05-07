package com.kclaw.agent.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test

class RetryingLlmClientFactoryTest {

    @Test
    fun `createRetryingPromptExecutor wraps koog retrying client with production config`() {
        val executor = createRetryingPromptExecutor(FakeLlmClient())

        executor.shouldBeInstanceOf<SingleLLMPromptExecutor>()

        val clientField = executor::class.java.getDeclaredField("llmClient")
        clientField.isAccessible = true
        val client = clientField.get(executor).shouldBeInstanceOf<RetryingLLMClient>()

        val configField = client::class.java.getDeclaredField("config")
        configField.isAccessible = true
        configField.get(client) shouldBe RetryConfig.PRODUCTION
    }

    private class FakeLlmClient : LLMClient() {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): List<Message.Response> = emptyList()

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>
        ): Flow<StreamFrame> = emptyFlow()

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            error("not used in this test")

        override fun llmProvider(): LLMProvider = LLMProvider.OpenAI

        override fun close() = Unit
    }
}
