package com.kclaw.agent.model

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider as KoogLLMProvider
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.kclaw.gateway.config.ProvidersConfig
import com.kclaw.protocol.identifiers.ModelId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class ModelRegistry(
    apiKeyResolver: (String) -> String? = { System.getenv(it) },
    providersConfig: ProvidersConfig = ProvidersConfig(),
    claudeCliCredentialReader: () -> ClaudeCliCredential? = { readClaudeCliCredentials() }
) {
    data class ModelAvailability(
        val ready: Boolean,
        val unavailableReason: String? = null
    )

    private val providerFactory = ProviderFactory(
        apiKeyResolver = apiKeyResolver,
        providersConfig = providersConfig,
        claudeCliCredentialReader = claudeCliCredentialReader
    )
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
        register(ModelDefinition(ModelId("anthropic:claude-opus-4-6"), "anthropic", "claude-opus-4-6", "Claude Opus 4.6", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-haiku-4-5"), "anthropic", "claude-haiku-4-5", "Claude Haiku 4.5", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-3-7"), "anthropic", "claude-sonnet-3-7", "Claude Sonnet 3.7", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-3-5"), "anthropic", "claude-sonnet-3-5", "Claude Sonnet 3.5", 200_000))
        register(ModelDefinition(ModelId("anthropic:claude-sonnet-4-6"), "anthropic", "claude-sonnet-4-6", "Claude Sonnet 4.6", 200_000))

        // OpenAI models
        register(ModelDefinition(ModelId("openai:gpt-4o"), "openai", "gpt-4o", "GPT-4o", 128_000))
        register(ModelDefinition(ModelId("openai:gpt-4.1"), "openai", "gpt-4.1", "GPT-4.1", 1_047_576))
        register(ModelDefinition(ModelId("openai:gpt-4.1-mini"), "openai", "gpt-4.1-mini", "GPT-4.1 Mini", 1_047_576))
        register(ModelDefinition(ModelId("openai:gpt-4.1-nano"), "openai", "gpt-4.1-nano", "GPT-4.1 Nano", 1_047_576))
        register(ModelDefinition(ModelId("openai:o3"), "openai", "o3", "O3", 200_000))
        register(ModelDefinition(ModelId("openai:o3-mini"), "openai", "o3-mini", "O3 Mini", 200_000))
        register(ModelDefinition(ModelId("openai:o4-mini"), "openai", "o4-mini", "O4 Mini", 200_000))
        register(ModelDefinition(ModelId("openai:gpt-5"), "openai", "gpt-5", "GPT-5", 1_047_576))
        register(ModelDefinition(ModelId("openai:gpt-5.2"), "openai", "gpt-5.2", "GPT-5.2", 400_000))
        register(ModelDefinition(ModelId("openai:gpt-5.2-pro"), "openai", "gpt-5.2-pro", "GPT-5.2 Pro", 400_000))
        register(ModelDefinition(ModelId("openai:gpt-5.2-codex"), "openai", "gpt-5.2-codex", "GPT-5.2-Codex", 400_000))
        register(ModelDefinition(ModelId("openai:gpt-5.2-chat-latest"), "openai", "gpt-5.2-chat-latest", "GPT-5.2 Chat", 128_000))
        register(ModelDefinition(ModelId("openai:gpt-5.3-codex"), "openai", "gpt-5.3-codex", "GPT-5.3-Codex", 400_000))
        register(ModelDefinition(ModelId("openai:gpt-5.3-chat-latest"), "openai", "gpt-5.3-chat-latest", "GPT-5.3 Chat", 128_000))
        register(ModelDefinition(ModelId("openai:gpt-5.4"), "openai", "gpt-5.4", "GPT-5.4", 1_050_000))
        register(ModelDefinition(ModelId("openai:gpt-5.4-pro"), "openai", "gpt-5.4-pro", "GPT-5.4 Pro", 1_050_000))

        // Google Gemini models
        register(ModelDefinition(ModelId("google:gemini-2.5-pro"), "google", "gemini-2.5-pro", "Gemini 2.5 Pro", 1_048_576))
        register(ModelDefinition(ModelId("google:gemini-2.5-flash"), "google", "gemini-2.5-flash", "Gemini 2.5 Flash", 1_048_576))
        register(ModelDefinition(ModelId("google:gemini-2.5-flash-lite"), "google", "gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", 1_048_576))
        register(ModelDefinition(ModelId("google:gemini-2.0-flash"), "google", "gemini-2.0-flash", "Gemini 2.0 Flash", 1_048_576))

        // DeepSeek models
        register(ModelDefinition(ModelId("deepseek:deepseek-chat"), "deepseek", "deepseek-chat", "DeepSeek Chat", 64_000))
        register(ModelDefinition(ModelId("deepseek:deepseek-reasoner"), "deepseek", "deepseek-reasoner", "DeepSeek Reasoner", 64_000))

        // Mistral models
        register(ModelDefinition(ModelId("mistral:mistral-medium-latest"), "mistral", "mistral-medium-latest", "Mistral Medium 3.1", 128_000))
        register(ModelDefinition(ModelId("mistral:mistral-large-latest"), "mistral", "mistral-large-latest", "Mistral Large 2.1", 128_000))
        register(ModelDefinition(ModelId("mistral:mistral-small-latest"), "mistral", "mistral-small-latest", "Mistral Small 2", 32_000))
        register(ModelDefinition(ModelId("mistral:magistral-medium-latest"), "mistral", "magistral-medium-latest", "Magistral Medium 1.2", 128_000))
        register(ModelDefinition(ModelId("mistral:codestral-latest"), "mistral", "codestral-latest", "Codestral", 256_000))
        register(ModelDefinition(ModelId("mistral:devstral-medium-latest"), "mistral", "devstral-medium-latest", "Devstral Medium", 128_000))

        // OpenRouter models
        register(ModelDefinition(ModelId("openrouter:anthropic/claude-sonnet-4.5"), "openrouter", "anthropic/claude-sonnet-4.5", "OpenRouter Claude Sonnet 4.5", 1_000_000))
        register(ModelDefinition(ModelId("openrouter:anthropic/claude-opus-4.5"), "openrouter", "anthropic/claude-opus-4.5", "OpenRouter Claude Opus 4.5", 200_000))
        register(ModelDefinition(ModelId("openrouter:openai/gpt-5"), "openrouter", "openai/gpt-5", "OpenRouter GPT-5", 400_000))
        register(ModelDefinition(ModelId("openrouter:openai/gpt-5-mini"), "openrouter", "openai/gpt-5-mini", "OpenRouter GPT-5 Mini", 400_000))
        register(ModelDefinition(ModelId("openrouter:microsoft/phi-4-reasoning:free"), "openrouter", "microsoft/phi-4-reasoning:free", "OpenRouter Phi-4 Reasoning (Free)", 32_768))
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

    fun availability(modelId: ModelId): ModelAvailability =
        availability(resolve(modelId))

    fun availability(definition: ModelDefinition): ModelAvailability =
        try {
            providerFactory.getExecutorForPrefix(definition.provider)
            resolveKoogModel(definition)
            ModelAvailability(ready = true)
        } catch (e: ModelRegistryError.MissingApiKey) {
            ModelAvailability(ready = false, unavailableReason = "Missing ${e.envVar}")
        } catch (e: ModelRegistryError.UnknownProvider) {
            ModelAvailability(ready = false, unavailableReason = "Unsupported provider: ${e.provider}")
        } catch (e: ModelRegistryError.UnknownModel) {
            ModelAvailability(ready = false, unavailableReason = "Unknown model: ${e.modelId.raw}")
        } catch (e: IllegalStateException) {
            ModelAvailability(ready = false, unavailableReason = e.message ?: "Provider configuration incomplete")
        } catch (e: Exception) {
            ModelAvailability(ready = false, unavailableReason = e.message ?: "Provider unavailable")
        }

    fun invalidateExecutorCache(providerPrefix: String? = null) {
        val provider = providerPrefix?.let {
            LLMProvider.fromPrefix(it) ?: throw ModelRegistryError.UnknownProvider(it)
        }
        providerFactory.invalidate(provider)
    }

    fun resolveKoogModel(definition: ModelDefinition): LLModel =
        when (definition.provider) {
            "anthropic" -> resolveAnthropicModel(definition)
            "openai" -> resolveOpenAIModel(definition)
            "google" -> resolveGoogleModel(definition)
            "deepseek" -> resolveDeepSeekModel(definition)
            "mistral" -> resolveMistralModel(definition)
            "openrouter" -> resolveOpenRouterModel(definition)
            "ollama" -> customModel(KoogLLMProvider.Ollama, definition)
            "azure", "groq", "xai", "together", "nvidia", "cerebras", "moonshot", "vllm", "litellm" ->
                customModel(KoogLLMProvider.OpenAI, definition)
            else -> throw ModelRegistryError.UnknownProvider(definition.provider)
        }

    fun list(): List<ModelDefinition> = catalog.values.toList()

    private fun resolveAnthropicModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, AnthropicModels.models, KoogLLMProvider.Anthropic)

    private fun resolveOpenAIModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, OpenAIModels.models, KoogLLMProvider.OpenAI)

    private fun resolveGoogleModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, GoogleModels.models, KoogLLMProvider.Google)

    private fun resolveDeepSeekModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, DeepSeekModels.models, KoogLLMProvider.DeepSeek)

    private fun resolveMistralModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, MistralAIModels.models, KoogLLMProvider.MistralAI)

    private fun resolveOpenRouterModel(definition: ModelDefinition): LLModel =
        resolveKnownOrCustom(definition, OpenRouterModels.models, KoogLLMProvider.OpenRouter)

    private fun resolveKnownOrCustom(
        definition: ModelDefinition,
        knownModels: List<LLModel>,
        provider: KoogLLMProvider
    ): LLModel =
        knownModels.firstOrNull { it.id == definition.modelName }
            ?: customModel(provider, definition)

    private fun customModel(provider: KoogLLMProvider, definition: ModelDefinition): LLModel =
        LLModel(
            provider = provider,
            id = definition.modelName,
            capabilities = defaultCapabilitiesFor(provider),
            contextLength = definition.maxContextTokens.toLong()
        )

    private companion object {
        private val DEFAULT_AGENT_CAPABILITIES = listOf(
            LLMCapability.Temperature,
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
            LLMCapability.Vision.Image,
            LLMCapability.Document
        )

        private val OPENAI_AGENT_CAPABILITIES = DEFAULT_AGENT_CAPABILITIES + listOf(
            LLMCapability.Speculation,
            LLMCapability.OpenAIEndpoint.Completions,
            LLMCapability.OpenAIEndpoint.Responses
        )

        private fun defaultCapabilitiesFor(provider: KoogLLMProvider): List<LLMCapability> =
            when (provider) {
                KoogLLMProvider.OpenAI -> OPENAI_AGENT_CAPABILITIES
                else -> DEFAULT_AGENT_CAPABILITIES
            }
    }
}
