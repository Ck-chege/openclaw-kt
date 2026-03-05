package com.openclaw.agent.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

private val logger = KotlinLogging.logger {}

/**
 * Wraps a [PromptExecutor] with automatic retry and exponential backoff
 * for rate-limit (429) and transient server errors (5xx).
 *
 * @param delegate The underlying executor to wrap
 * @param maxRetries Maximum number of retry attempts (default 3)
 * @param initialDelayMs Initial backoff delay in milliseconds (default 5000)
 * @param maxDelayMs Maximum backoff delay cap (default 60000)
 * @param backoffMultiplier Multiplier for exponential growth (default 2.0)
 */
class RetryingExecutor(
    private val delegate: PromptExecutor,
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 5_000,
    private val maxDelayMs: Long = 60_000,
    private val backoffMultiplier: Double = 2.0
) : PromptExecutor {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return retryWithBackoff("execute") {
            delegate.execute(prompt, model, tools)
        }
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        val resultFlow = retryWithBackoff("executeStreaming") {
            delegate.executeStreaming(prompt, model, tools)
        }
        emitAll(resultFlow)
    }

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<List<Message.Response>> {
        return retryWithBackoff("executeMultipleChoices") {
            delegate.executeMultipleChoices(prompt, model, tools)
        }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        return retryWithBackoff("moderate") {
            delegate.moderate(prompt, model)
        }
    }

    override suspend fun models(): List<LLModel> = delegate.models()

    override fun close() = delegate.close()

    private suspend fun <T> retryWithBackoff(
        operationName: String,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (!isRetryable(e)) {
                    logger.debug { "$operationName failed with non-retryable error: ${e.message}" }
                    throw e
                }

                if (attempt == maxRetries) {
                    logger.error { "$operationName failed after ${maxRetries + 1} attempts: ${e.message}" }
                    throw e
                }

                // Parse retry-after hint from error message if available
                val retryAfterMs = parseRetryAfter(e.message)
                val waitMs = if (retryAfterMs != null && retryAfterMs > 0) {
                    // Use server-suggested delay + small jitter
                    retryAfterMs + (0..1000).random()
                } else {
                    currentDelay + (0..1000).random()
                }

                logger.warn {
                    "$operationName rate-limited/transient error (attempt ${attempt + 1}/${maxRetries + 1}), " +
                        "retrying in ${waitMs}ms: ${e.message?.take(200)}"
                }

                delay(waitMs)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        throw lastException!!
    }

    companion object {
        /**
         * Determines if an exception is retryable (rate limit or transient server error).
         */
        internal fun isRetryable(e: Exception): Boolean {
            val message = e.message?.lowercase() ?: return false
            return message.contains("429") ||
                message.contains("rate_limit") ||
                message.contains("rate limit") ||
                message.contains("too many requests") ||
                message.contains("status code: 429") ||
                message.contains("status code: 500") ||
                message.contains("status code: 502") ||
                message.contains("status code: 503") ||
                message.contains("status code: 529") ||
                message.contains("overloaded") ||
                message.contains("capacity") ||
                message.contains("temporarily unavailable")
        }

        /**
         * Attempts to parse a "retry after" delay (in seconds) from the error message.
         * Looks for patterns like "Please try again in 10.468s" or "retry-after: 15".
         */
        internal fun parseRetryAfter(message: String?): Long? {
            if (message == null) return null
            // Pattern: "try again in X.XXs" or "try again in Xs"
            val tryAgainRegex = Regex("""try again in (\d+(?:\.\d+)?)s""", RegexOption.IGNORE_CASE)
            tryAgainRegex.find(message)?.let { match ->
                val seconds = match.groupValues[1].toDoubleOrNull() ?: return null
                return (seconds * 1000).toLong()
            }
            // Pattern: "retry-after: X"
            val retryAfterRegex = Regex("""retry-after:\s*(\d+)""", RegexOption.IGNORE_CASE)
            retryAfterRegex.find(message)?.let { match ->
                val seconds = match.groupValues[1].toLongOrNull() ?: return null
                return seconds * 1000
            }
            return null
        }
    }
}
