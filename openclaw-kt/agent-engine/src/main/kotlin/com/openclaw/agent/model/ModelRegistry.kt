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
    apiKeyResolver: (String) -> String? = { System.getenv(it) },
    claudeCliCredentialReader: () -> ClaudeCliCredential? = { readClaudeCliCredentials() }
) {
    private val providerFactory = ProviderFactory(apiKeyResolver, claudeCliCredentialReader)
    private val catalog = ConcurrentHashMap<String, ModelDefinition>()

    init {
        registerBuiltinModels()
    }

    private fun registerBuiltinModels() {
        // Anthropic models
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-4"), "anthropic", "claude-sonnet-4", "Claude Sonnet 4", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-4-5"), "anthropic", "claude-sonnet-4-5", "Claude Sonnet 4.5", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-opus-4"), "anthropic", "claude-opus-4", "Claude Opus 4", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-opus-4-1"), "anthropic", "claude-opus-4-1", "Claude Opus 4.1", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-opus-4-5"), "anthropic", "claude-opus-4-5", "Claude Opus 4.5", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-haiku-4-5"), "anthropic", "claude-haiku-4-5", "Claude Haiku 4.5", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-3-7"), "anthropic", "claude-sonnet-3-7", "Claude Sonnet 3.7", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-3-5"), "anthropic", "claude-sonnet-3-5", "Claude Sonnet 3.5", 200_000))

        // OpenAI models
        register(ModelDefinition(ModelId("openai:gpt-4o"), "openai", "gpt-4o", "GPT-4o", 128_000))
        register(ModelDefinition(ModelId("openai:gpt-4.1"), "openai", "gpt-4.1", "GPT-4.1", 1_047_576))
        register(ModelDefinition(ModelId("openai:gpt-4.1-mini"), "openai", "gpt-4.1-mini", "GPT-4.1 Mini", 1_047_576))
        register(ModelDefinition(ModelId("openai:gpt-4.1-nano"), "openai", "gpt-4.1-nano", "GPT-4.1 Nano", 1_047_576))
        register(ModelDefinition(ModelId("openai:o3"), "openai", "o3", "O3", 200_000))
        register(ModelDefinition(ModelId("openai:o3-mini"), "openai", "o3-mini", "O3 Mini", 200_000))
        register(ModelDefinition(ModelId("openai:o4-mini"), "openai", "o4-mini", "O4 Mini", 200_000))
        register(ModelDefinition(ModelId("openai:gpt-5"), "openai", "gpt-5", "GPT-5", 1_047_576))
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

    fun invalidateExecutorCache(providerPrefix: String? = null) {
        val provider = providerPrefix?.let {
            LLMProvider.fromPrefix(it) ?: throw ModelRegistryError.UnknownProvider(it)
        }
        providerFactory.invalidate(provider)
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
            "claude-sonnet-4" -> AnthropicModels.Sonnet_4
            "claude-sonnet-4-5" -> AnthropicModels.Sonnet_4_5
            "claude-opus-4" -> AnthropicModels.Opus_4
            "claude-opus-4-1" -> AnthropicModels.Opus_4_1
            "claude-opus-4-5" -> AnthropicModels.Opus_4_5
            "claude-haiku-4-5" -> AnthropicModels.Haiku_4_5
            "claude-sonnet-3-7" -> AnthropicModels.Sonnet_3_7
            "claude-sonnet-3-5" -> AnthropicModels.Sonnet_3_5
            else -> throw ModelRegistryError.UnknownModel(ModelId("anthropic:$modelName"))
        }

    private fun resolveOpenAIModel(modelName: String): LLModel =
        when (modelName) {
            "gpt-4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4.1" -> OpenAIModels.Chat.GPT4_1
            "gpt-4.1-mini" -> OpenAIModels.Chat.GPT4_1Mini
            "gpt-4.1-nano" -> OpenAIModels.Chat.GPT4_1Nano
            "o3" -> OpenAIModels.Chat.O3
            "o3-mini" -> OpenAIModels.Chat.O3Mini
            "o4-mini" -> OpenAIModels.Chat.O4Mini
            "gpt-5" -> OpenAIModels.Chat.GPT5
            else -> throw ModelRegistryError.UnknownModel(ModelId("openai:$modelName"))
        }
}
