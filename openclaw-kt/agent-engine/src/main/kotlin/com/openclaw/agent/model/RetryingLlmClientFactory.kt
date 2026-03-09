package com.openclaw.agent.model

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.toRetryingClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor

internal val DEFAULT_PROVIDER_RETRY_CONFIG: RetryConfig = RetryConfig.PRODUCTION

internal fun createRetryingPromptExecutor(
    client: LLMClient,
    retryConfig: RetryConfig = DEFAULT_PROVIDER_RETRY_CONFIG
): PromptExecutor = SingleLLMPromptExecutor(client.toRetryingClient(retryConfig))
