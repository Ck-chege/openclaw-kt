package com.openclaw.agent

import com.openclaw.gateway.config.GatewayConfig
import com.openclaw.gateway.config.SecretsConfig
import com.openclaw.protocol.identifiers.ModelId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AgentEngineTest {

    @Test
    fun `executor is recreated after provider key changes in config flow`() = runTest {
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
            scope = this
        )

        val first = engine.modelRegistry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
        val openAiFirst = engine.modelRegistry.resolveExecutor(ModelId("openai:gpt-4o"))

        configFlow.value = configFlow.value.copy(
            secrets = configFlow.value.secrets.copy(anthropicApiKey = "key-v2")
        )
        advanceUntilIdle()

        val second = engine.modelRegistry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
        val openAiSecond = engine.modelRegistry.resolveExecutor(ModelId("openai:gpt-4o"))
        (first === second) shouldBe false
        openAiSecond shouldBeSameInstanceAs openAiFirst
    }
}
