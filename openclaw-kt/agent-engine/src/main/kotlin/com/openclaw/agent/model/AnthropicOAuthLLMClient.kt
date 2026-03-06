package com.openclaw.agent.model

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.plugin
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * Anthropic LLM client that uses OAuth/setup-token Bearer authentication.
 *
 * Passes a custom Ktor [HttpClient] to the parent [AnthropicLLMClient] constructor.
 * The client intercepts all outgoing requests to:
 * - Replace `x-api-key` with `Authorization: Bearer <token>`
 * - Add OAuth-specific beta and identity headers
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
    /* apiKey   = */ "",
    /* settings = */ settings,
    /* httpClient = */ buildOAuthClient(tokenSupplier),
    /* clock    = */ Clock.System
) {
    companion object {
        internal fun buildOAuthClient(tokenSupplier: suspend () -> String): HttpClient {
            logger.debug { "Building Anthropic OAuth intercepting HttpClient" }
            val client = HttpClient(CIO) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                }
            }
            client.plugin(HttpSend).intercept { request ->
                val token = tokenSupplier()
                // Replace API-key auth with OAuth Bearer token
                request.headers.remove("x-api-key")
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                // OAuth-specific headers required by Anthropic
                request.headers.set("anthropic-version", "2023-06-01")
                request.headers.set("anthropic-beta", ANTHROPIC_OAUTH_BETAS.joinToString(","))
                request.headers.set(HttpHeaders.UserAgent, "claude-cli/2.1.2 (external, cli)")
                request.headers.set("x-app", "cli")
                execute(request)
            }
            return client
        }
    }
}
