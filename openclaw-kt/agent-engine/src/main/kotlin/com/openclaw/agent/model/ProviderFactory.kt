package com.openclaw.agent.model

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.azure.AzureOpenAIServiceVersion
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.model.PromptExecutor
import com.openclaw.gateway.config.ProvidersConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

// Secondary env var for explicit setup-token — more descriptive than ANTHROPIC_API_KEY
const val CLAUDE_SETUP_TOKEN_ENV = "CLAUDE_SETUP_TOKEN"

/**
 * All supported LLM providers.
 *
 * [prefix] is the string used in model refs (e.g. "openai/gpt-4o").
 * [envVar] is the primary environment variable for the API key.
 * [aliases] are alternate prefix spellings accepted in model refs.
 */
enum class LLMProvider(
    val prefix: String,
    val envVar: String,
    val aliases: List<String> = emptyList()
) {
    // ── First-class koog executors ────────────────────────────────────────────
    ANTHROPIC("anthropic", "ANTHROPIC_API_KEY", listOf("claude", "claude-cli")),
    OPENAI("openai", "OPENAI_API_KEY"),
    GOOGLE("google", "GEMINI_API_KEY", listOf("gemini", "google-gemini")),
    OPENROUTER("openrouter", "OPENROUTER_API_KEY"),
    OLLAMA("ollama", "OLLAMA_API_KEY"),          // key optional; uses baseUrl from config
    MISTRAL("mistral", "MISTRAL_API_KEY"),
    DEEPSEEK("deepseek", "DEEPSEEK_API_KEY"),

    // ── OpenAI-compatible (custom base URL) ───────────────────────────────────
    GROQ("groq", "GROQ_API_KEY"),
    XAI("xai", "XAI_API_KEY", listOf("grok")),
    TOGETHER("together", "TOGETHER_API_KEY", listOf("together-ai")),
    NVIDIA("nvidia", "NVIDIA_API_KEY"),
    CEREBRAS("cerebras", "CEREBRAS_API_KEY"),
    MOONSHOT("moonshot", "MOONSHOT_API_KEY", listOf("kimi")),
    VLLM("vllm", "VLLM_API_KEY"),               // local; baseUrl from config
    LITELLM("litellm", "LITELLM_API_KEY"),       // proxy; baseUrl from config

    // ── Needs special config ──────────────────────────────────────────────────
    AZURE("azure", "AZURE_OPENAI_API_KEY", listOf("azure-openai"));

    companion object {
        /** Base URLs for OpenAI-compatible providers (used when no config override). */
        val OPENAI_COMPAT_BASE_URLS = mapOf(
            GROQ to "https://api.groq.com/openai/v1",
            XAI to "https://api.x.ai/v1",
            TOGETHER to "https://api.together.xyz/v1",
            NVIDIA to "https://integrate.api.nvidia.com/v1",
            CEREBRAS to "https://api.cerebras.ai/v1",
            MOONSHOT to "https://api.moonshot.ai/v1"
        )

        fun fromPrefix(prefix: String): LLMProvider? {
            val normalized = prefix.trim().lowercase()
            return entries.find { p ->
                p.prefix == normalized || p.aliases.contains(normalized)
            }
        }
    }
}

/**
 * Factory that creates and caches [PromptExecutor] instances per provider.
 *
 * Supports all providers in [LLMProvider]. API keys are resolved in priority order:
 * 1. [SecretsConfig] in the config file
 * 2. Environment variables (using [LLMProvider.envVar])
 *
 * Thread-safe: uses [ConcurrentHashMap] for caching.
 */
class ProviderFactory(
    private val apiKeyResolver: (String) -> String? = { System.getenv(it) },
    private val providersConfig: ProvidersConfig = ProvidersConfig(),
    private val secretsResolver: (com.openclaw.gateway.config.SecretsConfig) -> Unit = {},
    private val secrets: com.openclaw.gateway.config.SecretsConfig = com.openclaw.gateway.config.SecretsConfig(),
    /** Override for testing — returns null to simulate no CLI credentials present. */
    private val claudeCliCredentialReader: () -> ClaudeCliCredential? = { readClaudeCliCredentials() }
) {
    private data class CachedExecutor(val cacheKey: String, val executor: PromptExecutor)
    private val executorCache = ConcurrentHashMap<LLMProvider, CachedExecutor>()

    // Credential manager for Anthropic — shared so token refresh state is preserved
    private var anthropicCredManager: AnthropicCredentialManager? = null

    fun getExecutor(provider: LLMProvider): PromptExecutor = when (provider) {
        LLMProvider.ANTHROPIC -> getAnthropicExecutor()
        LLMProvider.OPENAI    -> getOpenAIExecutor()
        LLMProvider.GOOGLE    -> getGoogleExecutor()
        LLMProvider.OPENROUTER -> getOpenRouterExecutor()
        LLMProvider.MISTRAL   -> getMistralExecutor()
        LLMProvider.OLLAMA    -> getOllamaExecutor()
        LLMProvider.DEEPSEEK  -> getDeepSeekExecutor()
        LLMProvider.AZURE     -> getAzureExecutor()
        // All OpenAI-compatible providers with hardcoded base URLs
        LLMProvider.GROQ, LLMProvider.XAI, LLMProvider.TOGETHER,
        LLMProvider.NVIDIA, LLMProvider.CEREBRAS, LLMProvider.MOONSHOT ->
            getOpenAICompatExecutor(provider, LLMProvider.OPENAI_COMPAT_BASE_URLS[provider]!!)
        LLMProvider.VLLM      -> getOpenAICompatExecutor(provider, providersConfig.vllm.baseUrl)
        LLMProvider.LITELLM   -> getOpenAICompatExecutor(provider, providersConfig.litellm.baseUrl)
    }

    // ── Anthropic ─────────────────────────────────────────────────────────────

    private fun getAnthropicExecutor(): PromptExecutor {
        val explicitKey = resolveSecret(LLMProvider.ANTHROPIC.envVar, secrets.anthropicApiKey)
            ?: resolveSecret(CLAUDE_SETUP_TOKEN_ENV, null)

        val mode = detectAnthropicAuthMode(explicitKey)
        val cacheKey = explicitKey ?: "claude-cli"

        val cached = executorCache[LLMProvider.ANTHROPIC]
        if (cached != null && cached.cacheKey == cacheKey) return cached.executor

        logger.info { "Creating Anthropic executor (mode: $mode)" }

        val client = when (mode) {
            AnthropicAuthMode.API_KEY -> {
                logger.info { "Using Anthropic API key auth" }
                AnthropicLLMClient(explicitKey!!, AnthropicClientSettings())
            }
            AnthropicAuthMode.OAUTH_TOKEN, AnthropicAuthMode.CLAUDE_CLI -> {
                val manager = AnthropicCredentialManager(explicitKey = explicitKey, homeDir = null)
                anthropicCredManager = manager

                if (mode == AnthropicAuthMode.CLAUDE_CLI) {
                    val cliCred = claudeCliCredentialReader()
                        ?: throw ModelRegistryError.MissingApiKey(LLMProvider.ANTHROPIC.prefix, LLMProvider.ANTHROPIC.envVar)
                    logger.info {
                        "Using Claude CLI OAuth (type=${if (cliCred.canRefresh()) "full-oauth" else "setup-token-only"}, " +
                        "expires=${java.util.Date(cliCred.expiresAt)})"
                    }
                } else {
                    logger.info { "Using Anthropic setup-token (OAuth, no auto-refresh)" }
                }

                createAnthropicOAuthClient(tokenSupplier = { manager.getAccessToken() })
            }
        }

        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.ANTHROPIC] = CachedExecutor(cacheKey, it)
        }
    }

    private fun getOpenAIExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.OPENAI.envVar, secrets.openaiApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.OPENAI.prefix, LLMProvider.OPENAI.envVar)

        val cached = executorCache[LLMProvider.OPENAI]
        if (cached != null && cached.cacheKey == apiKey) return cached.executor

        logger.info { "Creating openai executor" }
        val client = OpenAILLMClient(apiKey, OpenAIClientSettings())
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.OPENAI] = CachedExecutor(apiKey, it)
        }
    }

    private fun getGoogleExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.GOOGLE.envVar, secrets.googleApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.GOOGLE.prefix, LLMProvider.GOOGLE.envVar)

        val cached = executorCache[LLMProvider.GOOGLE]
        if (cached != null && cached.cacheKey == apiKey) return cached.executor

        logger.info { "Creating google executor" }
        val client = GoogleLLMClient(apiKey, GoogleClientSettings())
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.GOOGLE] = CachedExecutor(apiKey, it)
        }
    }

    // ── Ollama ────────────────────────────────────────────────────────────────

    private fun getOllamaExecutor(): PromptExecutor {
        val baseUrl = providersConfig.ollama.baseUrl
        val cached = executorCache[LLMProvider.OLLAMA]
        if (cached != null && cached.cacheKey == baseUrl) return cached.executor

        logger.info { "Creating Ollama executor (baseUrl=$baseUrl)" }
        val client = OllamaClient(baseUrl = baseUrl)
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.OLLAMA] = CachedExecutor(baseUrl, it)
        }
    }

    // ── DeepSeek ──────────────────────────────────────────────────────────────

    private fun getDeepSeekExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.DEEPSEEK.envVar, secrets.deepseekApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.DEEPSEEK.prefix, LLMProvider.DEEPSEEK.envVar)

        val cached = executorCache[LLMProvider.DEEPSEEK]
        if (cached != null && cached.cacheKey == apiKey) return cached.executor

        logger.info { "Creating DeepSeek executor" }
        val client = DeepSeekLLMClient(apiKey, DeepSeekClientSettings())
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.DEEPSEEK] = CachedExecutor(apiKey, it)
        }
    }

    private fun getMistralExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.MISTRAL.envVar, secrets.mistralApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.MISTRAL.prefix, LLMProvider.MISTRAL.envVar)

        val cached = executorCache[LLMProvider.MISTRAL]
        if (cached != null && cached.cacheKey == apiKey) return cached.executor

        logger.info { "Creating mistral executor" }
        val client = MistralAILLMClient(apiKey, MistralAIClientSettings())
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.MISTRAL] = CachedExecutor(apiKey, it)
        }
    }

    private fun getOpenRouterExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.OPENROUTER.envVar, secrets.openrouterApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.OPENROUTER.prefix, LLMProvider.OPENROUTER.envVar)

        val cached = executorCache[LLMProvider.OPENROUTER]
        if (cached != null && cached.cacheKey == apiKey) return cached.executor

        logger.info { "Creating openrouter executor" }
        val client = OpenRouterLLMClient(apiKey, OpenRouterClientSettings())
        return createRetryingPromptExecutor(client).also {
            executorCache[LLMProvider.OPENROUTER] = CachedExecutor(apiKey, it)
        }
    }

    // ── Azure OpenAI ──────────────────────────────────────────────────────────

    private fun getAzureExecutor(): PromptExecutor {
        val apiKey = resolveSecret(LLMProvider.AZURE.envVar, secrets.azureApiKey)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.AZURE.prefix, LLMProvider.AZURE.envVar)
        val endpoint = providersConfig.azure.endpoint
            ?: throw IllegalStateException("Azure provider requires providers.azure.endpoint in config")
        val deployment = providersConfig.azure.deployment
            ?: throw IllegalStateException("Azure provider requires providers.azure.deployment in config")

        val cacheKey = "$apiKey:$endpoint:$deployment"
        val cached = executorCache[LLMProvider.AZURE]
        if (cached != null && cached.cacheKey == cacheKey) return cached.executor

        logger.info { "Creating Azure OpenAI executor (endpoint=$endpoint, deployment=$deployment)" }

        val azureClient = OpenAILLMClient(
            apiKey = apiKey,
            settings = AzureOpenAIClientSettings(
                baseUrl = endpoint,
                version = AzureOpenAIServiceVersion.fromString(providersConfig.azure.apiVersion)
            )
        )
        return createRetryingPromptExecutor(azureClient).also {
            executorCache[LLMProvider.AZURE] = CachedExecutor(cacheKey, it)
        }
    }

    // ── Generic OpenAI-compatible ─────────────────────────────────────────────

    private fun getOpenAICompatExecutor(provider: LLMProvider, baseUrl: String): PromptExecutor {
        val secretField = when (provider) {
            LLMProvider.GROQ      -> secrets.groqApiKey
            LLMProvider.XAI       -> secrets.xaiApiKey
            LLMProvider.TOGETHER  -> secrets.togetherApiKey
            LLMProvider.NVIDIA    -> secrets.nvidiaApiKey
            LLMProvider.CEREBRAS  -> secrets.cerebrasApiKey
            LLMProvider.MOONSHOT  -> secrets.moonshotApiKey
            LLMProvider.VLLM      -> secrets.vllmApiKey
            LLMProvider.LITELLM   -> secrets.litellmApiKey
            else -> null
        }
        val apiKey = resolveSecret(provider.envVar, secretField)
            ?: run {
                // Some local providers (vllm, litellm) don't need a key — use placeholder
                if (provider == LLMProvider.VLLM || provider == LLMProvider.LITELLM) "no-key"
                else throw ModelRegistryError.MissingApiKey(provider.prefix, provider.envVar)
            }

        val cacheKey = "$apiKey:$baseUrl"
        val cached = executorCache[provider]
        if (cached != null && cached.cacheKey == cacheKey) return cached.executor

        logger.info { "Creating ${provider.prefix} executor (baseUrl=$baseUrl)" }
        val client = OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl))
        return createRetryingPromptExecutor(client).also {
            executorCache[provider] = CachedExecutor(cacheKey, it)
        }
    }

    // ── Key resolution ────────────────────────────────────────────────────────

    /**
     * Resolves an API key: config value takes priority over env var.
     */
    private fun resolveSecret(envVar: String, configValue: String?): String? {
        val fromConfig = configValue?.trim()?.takeIf { it.isNotEmpty() }
        if (fromConfig != null) return fromConfig
        return apiKeyResolver(envVar)?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun getExecutorForPrefix(prefix: String): PromptExecutor {
        val provider = LLMProvider.fromPrefix(prefix)
            ?: throw ModelRegistryError.UnknownProvider(prefix)
        return getExecutor(provider)
    }

    fun clear(provider: LLMProvider? = null) {
        if (provider == null) executorCache.clear()
        else executorCache.remove(provider)
    }

    fun invalidate(provider: LLMProvider? = null) = clear(provider)
}
