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
        // Phase 1: Auto-import from Koog's maintained model catalogs.
        // New models added to Koog are picked up automatically.
        importKoogCatalog("anthropic", AnthropicModels.models)
        importKoogCatalog("openai", OpenAIModels.models)
        importKoogCatalog("google", GoogleModels.models)
        importKoogCatalog("deepseek", DeepSeekModels.models)
        importKoogCatalog("mistral", MistralAIModels.models)
        importKoogCatalog("openrouter", OpenRouterModels.models)

        // Phase 2: Fill in models not yet in Koog catalogs.
        // registerIfAbsent is a no-op for models already imported above.
        // Remove entries here as Koog adds them.

        // Anthropic — models not yet in Koog 0.7.0
        registerIfAbsent(ModelDefinition(ModelId("anthropic:claude-sonnet-4-6"), "anthropic", "claude-sonnet-4-6", "Claude Sonnet 4.6", 200_000))
        registerIfAbsent(ModelDefinition(ModelId("anthropic:claude-opus-4-6"), "anthropic", "claude-opus-4-6", "Claude Opus 4.6", 200_000))

        // OpenAI — models not yet in Koog 0.7.0
        registerIfAbsent(ModelDefinition(ModelId("openai:gpt-5.4"), "openai", "gpt-5.4", "GPT-5.4", 1_050_000))
        registerIfAbsent(ModelDefinition(ModelId("openai:gpt-5.4-pro"), "openai", "gpt-5.4-pro", "GPT-5.4 Pro", 1_050_000))

        // Google Gemini
        registerIfAbsent(ModelDefinition(ModelId("google:gemini-2.5-pro"), "google", "gemini-2.5-pro", "Gemini 2.5 Pro", 1_048_576))
        registerIfAbsent(ModelDefinition(ModelId("google:gemini-2.5-flash"), "google", "gemini-2.5-flash", "Gemini 2.5 Flash", 1_048_576))
        registerIfAbsent(ModelDefinition(ModelId("google:gemini-2.5-flash-lite"), "google", "gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", 1_048_576))
        registerIfAbsent(ModelDefinition(ModelId("google:gemini-2.0-flash"), "google", "gemini-2.0-flash", "Gemini 2.0 Flash", 1_048_576))

        // DeepSeek
        registerIfAbsent(ModelDefinition(ModelId("deepseek:deepseek-chat"), "deepseek", "deepseek-chat", "DeepSeek Chat", 64_000))
        registerIfAbsent(ModelDefinition(ModelId("deepseek:deepseek-reasoner"), "deepseek", "deepseek-reasoner", "DeepSeek Reasoner", 64_000))

        // Mistral
        registerIfAbsent(ModelDefinition(ModelId("mistral:mistral-medium-latest"), "mistral", "mistral-medium-latest", "Mistral Medium", 128_000))
        registerIfAbsent(ModelDefinition(ModelId("mistral:mistral-large-latest"), "mistral", "mistral-large-latest", "Mistral Large", 128_000))
        registerIfAbsent(ModelDefinition(ModelId("mistral:mistral-small-latest"), "mistral", "mistral-small-latest", "Mistral Small", 32_000))
        registerIfAbsent(ModelDefinition(ModelId("mistral:magistral-medium-latest"), "mistral", "magistral-medium-latest", "Magistral Medium", 128_000))
        registerIfAbsent(ModelDefinition(ModelId("mistral:codestral-latest"), "mistral", "codestral-latest", "Codestral", 256_000))
        registerIfAbsent(ModelDefinition(ModelId("mistral:devstral-medium-latest"), "mistral", "devstral-medium-latest", "Devstral Medium", 128_000))

        // OpenRouter
        registerIfAbsent(ModelDefinition(ModelId("openrouter:anthropic/claude-sonnet-4.5"), "openrouter", "anthropic/claude-sonnet-4.5", "OpenRouter Claude Sonnet 4.5", 1_000_000))
        registerIfAbsent(ModelDefinition(ModelId("openrouter:anthropic/claude-opus-4.5"), "openrouter", "anthropic/claude-opus-4.5", "OpenRouter Claude Opus 4.5", 200_000))
        registerIfAbsent(ModelDefinition(ModelId("openrouter:openai/gpt-5"), "openrouter", "openai/gpt-5", "OpenRouter GPT-5", 400_000))
        registerIfAbsent(ModelDefinition(ModelId("openrouter:openai/gpt-5-mini"), "openrouter", "openai/gpt-5-mini", "OpenRouter GPT-5 Mini", 400_000))
        registerIfAbsent(ModelDefinition(ModelId("openrouter:microsoft/phi-4-reasoning:free"), "openrouter", "microsoft/phi-4-reasoning:free", "OpenRouter Phi-4 Reasoning (Free)", 32_768))
    }

    /**
     * Imports all models from a Koog provider catalog, creating [ModelDefinition] entries
     * with display names derived from model IDs and context lengths from Koog metadata.
     */
    private fun importKoogCatalog(providerPrefix: String, models: List<LLModel>) {
        for (model in models) {
            register(ModelDefinition(
                id = ModelId("$providerPrefix:${model.id}"),
                provider = providerPrefix,
                modelName = model.id,
                displayName = formatDisplayName(model.id),
                maxContextTokens = (model.contextLength ?: 200_000L).toInt()
            ))
        }
        logger.debug { "Imported ${models.size} models from Koog catalog: $providerPrefix" }
    }

    /**
     * Registers a model only if not already present (e.g. not already imported from Koog).
     */
    private fun registerIfAbsent(definition: ModelDefinition) {
        catalog.putIfAbsent(definition.id.raw, definition)
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

        /**
         * Derives a human-readable display name from a model ID string.
         * Handles GPT-style ("gpt-4o" -> "GPT-4o"), o-series ("o3-mini" -> "O3 Mini"),
         * DeepSeek, and generic dash-separated names with version dot-joining.
         */
        private fun formatDisplayName(modelId: String): String {
            // OpenRouter slash-separated IDs: "anthropic/claude-sonnet-4.5"
            if ('/' in modelId) {
                val model = modelId.substringAfter("/")
                return "OpenRouter ${formatDisplayName(model)}"
            }
            // GPT models: "gpt-4o" -> "GPT-4o", "gpt-4.1-mini" -> "GPT-4.1 Mini"
            if (modelId.startsWith("gpt-")) {
                val rest = modelId.removePrefix("gpt-")
                val parts = rest.split("-")
                val version = parts.first()
                val suffix = parts.drop(1).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                return if (suffix.isEmpty()) "GPT-$version" else "GPT-$version $suffix"
            }
            // O-series: "o3" -> "O3", "o3-mini" -> "O3 Mini"
            if (modelId.matches(Regex("^o\\d.*"))) {
                return modelId.split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
            // Default: split on hyphens, join consecutive digit-only segments with "."
            val parts = modelId.split("-")
            return buildString {
                for ((i, part) in parts.withIndex()) {
                    if (i > 0) {
                        val prev = parts[i - 1]
                        if (prev.all { it.isDigit() } && part.all { it.isDigit() }) append(".")
                        else append(" ")
                    }
                    append(when {
                        part.equals("deepseek", ignoreCase = true) -> "DeepSeek"
                        part.equals("ai", ignoreCase = true) -> "AI"
                        part.all { it.isDigit() || it == '.' } -> part
                        else -> part.replaceFirstChar { it.uppercase() }
                    })
                }
            }
        }
    }
}