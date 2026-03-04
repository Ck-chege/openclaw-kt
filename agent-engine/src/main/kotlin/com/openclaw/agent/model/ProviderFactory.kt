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
    private val executorCache = ConcurrentHashMap<LLMProvider, PromptExecutor>()

    fun getExecutor(provider: LLMProvider): PromptExecutor =
        executorCache.getOrPut(provider) {
            val apiKey = apiKeyResolver(provider.envVar)
                ?: throw ModelRegistryError.MissingApiKey(provider.prefix, provider.envVar)

            logger.info { "Creating executor for provider: ${provider.prefix}" }
            when (provider) {
                LLMProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
                LLMProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
            }
        }

    fun getExecutorForPrefix(prefix: String): PromptExecutor {
        val provider = LLMProvider.fromPrefix(prefix)
            ?: throw ModelRegistryError.UnknownProvider(prefix)
        return getExecutor(provider)
    }
}
