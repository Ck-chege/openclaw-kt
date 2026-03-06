package com.openclaw.agent.model

import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

// Secondary env var for explicit setup-token — more descriptive than ANTHROPIC_API_KEY
const val CLAUDE_SETUP_TOKEN_ENV = "CLAUDE_SETUP_TOKEN"

enum class LLMProvider(val prefix: String, val envVar: String) {
    ANTHROPIC("anthropic", "ANTHROPIC_API_KEY"),
    OPENAI("openai", "OPENAI_API_KEY");

    companion object {
        fun fromPrefix(prefix: String): LLMProvider? =
            entries.find { it.prefix == prefix.lowercase() }
    }
}

class ProviderFactory(
    private val apiKeyResolver: (String) -> String? = { System.getenv(it) },
    /** Override for testing — returns null to simulate no CLI credentials present. */
    private val claudeCliCredentialReader: () -> ClaudeCliCredential? = { readClaudeCliCredentials() }
) {
    private data class CachedExecutor(
        val cacheKey: String,
        val executor: PromptExecutor
    )

    private val executorCache = ConcurrentHashMap<LLMProvider, CachedExecutor>()

    // Credential manager for Anthropic — shared so token refresh state is preserved
    private var anthropicCredManager: AnthropicCredentialManager? = null

    fun getExecutor(provider: LLMProvider): PromptExecutor {
        return when (provider) {
            LLMProvider.ANTHROPIC -> getAnthropicExecutor()
            LLMProvider.OPENAI -> getOpenAiExecutor()
        }
    }

    private fun getAnthropicExecutor(): PromptExecutor {
        // Resolve explicit key from config — check CLAUDE_SETUP_TOKEN first, then ANTHROPIC_API_KEY
        val explicitKey = apiKeyResolver(CLAUDE_SETUP_TOKEN_ENV)?.trim()?.takeIf { it.isNotEmpty() }
            ?: apiKeyResolver(LLMProvider.ANTHROPIC.envVar)?.trim()?.takeIf { it.isNotEmpty() }

        val mode = detectAnthropicAuthMode(explicitKey)
        val cacheKey = explicitKey ?: "claude-cli"

        val cached = executorCache[LLMProvider.ANTHROPIC]
        if (cached != null && cached.cacheKey == cacheKey) {
            return cached.executor
        }

        logger.info { "Creating Anthropic executor (mode: $mode)" }

        val baseExecutor = when (mode) {
            AnthropicAuthMode.API_KEY -> {
                // Regular API key — use Koog's standard executor
                val key = explicitKey!!
                logger.info { "Using Anthropic API key auth" }
                simpleAnthropicExecutor(key)
            }
            AnthropicAuthMode.OAUTH_TOKEN, AnthropicAuthMode.CLAUDE_CLI -> {
                // OAuth path — create credential manager and OAuth executor
                val manager = AnthropicCredentialManager(
                    explicitKey = explicitKey,
                    homeDir = null
                )
                anthropicCredManager = manager

                if (mode == AnthropicAuthMode.OAUTH_TOKEN) {
                    logger.info { "Using Anthropic setup-token (OAuth, no auto-refresh)" }
                } else {
                    // CLAUDE_CLI mode: check if credentials are actually available
                    val cliCred = claudeCliCredentialReader()
                    if (cliCred != null) {
                        logger.info {
                            "Using Claude CLI OAuth credentials " +
                            "(type=${if (cliCred.canRefresh()) "full-oauth" else "setup-token-only"}, " +
                            "expires=${java.util.Date(cliCred.expiresAt)})"
                        }
                    } else {
                        // No CLI credentials and no explicit key — fail early with a clear error
                        throw ModelRegistryError.MissingApiKey(
                            LLMProvider.ANTHROPIC.prefix,
                            LLMProvider.ANTHROPIC.envVar
                        )
                    }
                }

                createAnthropicOAuthExecutor(tokenSupplier = { manager.getAccessToken() })
            }
        }

        val wrapped = RetryingExecutor(baseExecutor)
        executorCache[LLMProvider.ANTHROPIC] = CachedExecutor(cacheKey = cacheKey, executor = wrapped)
        return wrapped
    }

    private fun getOpenAiExecutor(): PromptExecutor {
        val apiKey = apiKeyResolver(LLMProvider.OPENAI.envVar)
            ?: throw ModelRegistryError.MissingApiKey(LLMProvider.OPENAI.prefix, LLMProvider.OPENAI.envVar)

        val cached = executorCache[LLMProvider.OPENAI]
        if (cached != null && cached.cacheKey == apiKey) {
            return cached.executor
        }

        logger.info { "Creating OpenAI executor" }
        val wrapped = RetryingExecutor(simpleOpenAIExecutor(apiKey))
        executorCache[LLMProvider.OPENAI] = CachedExecutor(cacheKey = apiKey, executor = wrapped)
        return wrapped
    }

    fun getExecutorForPrefix(prefix: String): PromptExecutor {
        val provider = LLMProvider.fromPrefix(prefix)
            ?: throw ModelRegistryError.UnknownProvider(prefix)
        return getExecutor(provider)
    }

    fun clear(provider: LLMProvider? = null) {
        if (provider == null) {
            executorCache.clear()
            return
        }
        executorCache.remove(provider)
    }

    fun invalidate(provider: LLMProvider? = null) = clear(provider)
}
