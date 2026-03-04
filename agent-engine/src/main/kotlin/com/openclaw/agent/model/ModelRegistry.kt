package com.openclaw.agent.model

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.openclaw.protocol.identifiers.ModelId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class ModelRegistry(
    apiKeyResolver: (String) -> String? = { System.getenv(it) }
) {
    private val providerFactory = ProviderFactory(apiKeyResolver)
    private val catalog = ConcurrentHashMap<String, ModelDefinition>()

    init {
        registerBuiltinModels()
    }

    private fun registerBuiltinModels() {
        register(
            ModelDefinition(
                id = ModelId("anthropic:claude-sonnet-4-5"),
                provider = "anthropic",
                modelName = "claude-sonnet-4-5",
                displayName = "Claude Sonnet 4.5",
                maxContextTokens = 200_000
            )
        )
        register(
            ModelDefinition(
                id = ModelId("anthropic:claude-opus-4-6"),
                provider = "anthropic",
                modelName = "claude-opus-4-6",
                displayName = "Claude Opus 4.6",
                maxContextTokens = 200_000
            )
        )
        register(
            ModelDefinition(
                id = ModelId("openai:gpt-4.1"),
                provider = "openai",
                modelName = "gpt-4.1",
                displayName = "GPT-4.1",
                maxContextTokens = 128_000
            )
        )
        register(
            ModelDefinition(
                id = ModelId("openai:gpt-4o"),
                provider = "openai",
                modelName = "gpt-4o",
                displayName = "GPT-4o",
                maxContextTokens = 128_000
            )
        )
    }

    fun register(definition: ModelDefinition) {
        logger.debug { "Registering model: ${definition.id.raw}" }
        catalog[definition.id.raw] = definition
    }

    fun resolve(modelId: ModelId): ModelDefinition =
        catalog[modelId.raw]
            ?: throw ModelRegistryError.UnknownModel(modelId)

    fun resolveExecutor(modelId: ModelId): PromptExecutor {
        val definition = resolve(modelId)
        return providerFactory.getExecutorForPrefix(definition.provider)
    }

    fun resolveKoogModel(definition: ModelDefinition): LLModel =
        when (definition.provider) {
            "anthropic" -> resolveAnthropicModel(definition.modelName)
            "openai" -> resolveOpenAIModel(definition.modelName)
            else -> throw ModelRegistryError.UnknownProvider(definition.provider)
        }

    fun list(): List<ModelDefinition> = catalog.values.toList()

    private fun resolveAnthropicModel(modelName: String): LLModel =
        when (modelName) {
            "claude-sonnet-4-5" -> AnthropicModels.Sonnet_4_5
            "claude-opus-4-6" -> AnthropicModels.Opus_4_5
            "claude-sonnet-4" -> AnthropicModels.Sonnet_4
            "claude-opus-4" -> AnthropicModels.Opus_4
            "claude-haiku-4-5" -> AnthropicModels.Haiku_4_5
            "claude-sonnet-3-7" -> AnthropicModels.Sonnet_3_7
            "claude-sonnet-3-5" -> AnthropicModels.Sonnet_3_5
            else -> throw ModelRegistryError.UnknownModel(ModelId("anthropic:$modelName"))
        }

    private fun resolveOpenAIModel(modelName: String): LLModel =
        when (modelName) {
            "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            else -> throw ModelRegistryError.UnknownModel(ModelId("openai:$modelName"))
        }
}
