package com.kclaw.agent.model

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

// ── OAuth Ktor Plugin ─────────────────────────────────────────────────────────

private class AnthropicOAuthConfig {
    lateinit var tokenSupplier: suspend () -> String
    var betas: List<String> = ANTHROPIC_OAUTH_BETAS
    var userAgent: String = "claude-cli/2.1.2 (external, cli)"
}

/**
 * Ktor client plugin that replaces Anthropic's `x-api-key` auth with OAuth Bearer tokens.
 *
 * By being a proper `createClientPlugin` plugin, its configuration is preserved when
 * [AnthropicLLMClient] creates internal child clients via `.config {}`. The `onRequest`
 * hook runs in `HttpRequestPipeline.State` — after `DefaultRequest` (`Before` phase)
 * has applied the `x-api-key` header — so we can cleanly remove and replace it.
 */
private val AnthropicOAuthPlugin = createClientPlugin("AnthropicOAuth", ::AnthropicOAuthConfig) {
    val tokenSupplier = pluginConfig.tokenSupplier
    val betas = pluginConfig.betas
    val userAgent = pluginConfig.userAgent

    onRequest { request, _ ->
        val token = tokenSupplier()
        // Remove API-key auth; replace with OAuth Bearer token
        request.headers.remove("x-api-key")
        request.headers.remove(HttpHeaders.Authorization)
        request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        // Required OAuth headers
        request.headers.set("anthropic-version", "2023-06-01")
        request.headers.set("anthropic-beta", betas.joinToString(","))
        request.headers.set(HttpHeaders.UserAgent, userAgent)
        request.headers.set("x-app", "cli")
    }
}

// ── LLM Client ───────────────────────────────────────────────────────────────

/**
 * Anthropic LLM client that uses OAuth/setup-token Bearer authentication.
 *
 * Passes a custom Ktor [HttpClient] to the parent [AnthropicLLMClient] constructor.
 * The client has [AnthropicOAuthPlugin] installed so that OAuth headers are injected
 * (and `x-api-key` removed) on every outgoing request, including those made by child
 * clients that [AnthropicLLMClient] creates internally.
 *
 * The [tokenSupplier] is called per-request so refreshed tokens are always used.
 *
 * @param tokenSupplier Called before each request to get a (possibly refreshed) token
 * @param settings Optional [AnthropicClientSettings] override (timeouts, etc.)
 */
class AnthropicOAuthLLMClient(
    tokenSupplier: suspend () -> String,
    settings: AnthropicClientSettings = AnthropicClientSettings()
) : AnthropicLLMClient(
    /* apiKey     = */ "",
    /* settings   = */ settings,
    /* httpClient = */ buildOAuthClient(tokenSupplier),
    /* clock      = */ Clock.System
) {
    companion object {
        internal fun buildOAuthClient(tokenSupplier: suspend () -> String): HttpClient {
            logger.debug { "Building Anthropic OAuth HttpClient" }
            return HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                }
                install(AnthropicOAuthPlugin) {
                    this.tokenSupplier = tokenSupplier
                }
            }
        }
    }
}
