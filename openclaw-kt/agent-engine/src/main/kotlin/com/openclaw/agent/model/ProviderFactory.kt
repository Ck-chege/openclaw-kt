package com.openclaw.agent.model

import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

enum class LLMProvider(val prefix: String, val envVar: String) {
    ANTHROPIC("anthropic", "ANTHROPIC_API_KEY"),
    OPENAI("openai", "OPENAI_API_KEY");



    companion object {
        fun fromPrefix(prefix: String): LLMProvider? =
            entries.find { it.prefix == prefix.lowercase() }
    }
}

class ProviderFactory(
    private val apiKeyResolver: (String) -> String? = { System.getenv(it) }
) {
    private data class CachedExecutor(
        val apiKey: String,
        val executor: PromptExecutor
    )

    private val executorCache = ConcurrentHashMap<LLMProvider, CachedExecutor>()

    fun getExecutor(provider: LLMProvider): PromptExecutor {
        val apiKey = apiKeyResolver(provider.envVar)
            ?: throw ModelRegistryError.MissingApiKey(provider.prefix, provider.envVar)
        val cached = executorCache[provider]
        if (cached != null && cached.apiKey == apiKey) {
            return cached.executor
        }

        logger.info { "Creating executor for provider: ${provider.prefix}" }
        val baseExecutor = when (provider) {
            LLMProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
            LLMProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
        }
        val wrapped = RetryingExecutor(baseExecutor)
        executorCache[provider] = CachedExecutor(apiKey = apiKey, executor = wrapped)
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
