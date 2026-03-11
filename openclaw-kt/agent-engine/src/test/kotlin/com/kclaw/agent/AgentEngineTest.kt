package com.kclaw.agent

import com.kclaw.gateway.config.GatewayConfig
import com.kclaw.gateway.config.SecretsConfig
import com.kclaw.protocol.identifiers.ModelId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentEngineTest {

    @Test
    fun `executor is recreated after provider key changes once anthropic cache is invalidated`() = runTest {
        val configFlow = MutableStateFlow(
            GatewayConfig(
                secrets = SecretsConfig(
                    anthropicApiKey = "key-v1",
                    openaiApiKey = "openai-stable"
                )
            )
        )
        val engine = AgentEngine(
            apiKeyResolver = { null },
            configFlow = configFlow,
            scope = backgroundScope
        )
        advanceUntilIdle()

        val first = engine.modelRegistry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
        val openAiFirst = engine.modelRegistry.resolveExecutor(ModelId("openai:gpt-4o"))

        configFlow.value = configFlow.value.copy(
            secrets = configFlow.value.secrets.copy(anthropicApiKey = "key-v2")
        )
        advanceUntilIdle()
        engine.invalidateModelExecutors("anthropic")

        val second = engine.modelRegistry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
        val openAiSecond = engine.modelRegistry.resolveExecutor(ModelId("openai:gpt-4o"))
        (first === second) shouldBe false
        openAiSecond shouldBeSameInstanceAs openAiFirst
    }
}
