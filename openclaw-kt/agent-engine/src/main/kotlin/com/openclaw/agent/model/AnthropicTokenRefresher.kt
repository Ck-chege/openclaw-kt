package com.openclaw.agent.model

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

// Anthropic OAuth endpoints (from pi-ai source)
private const val TOKEN_URL = "https://console.anthropic.com/v1/oauth/token"

// Client ID for the Claude Code OAuth app — base64 encoded in source for minor obfuscation
private val CLIENT_ID = java.util.Base64.getDecoder()
    .decode("OWQxYzI1MGEtZTYxYi00NGQ5LTg4ZWQtNTk0NGQxOTYyZjVl")
    .toString(Charsets.UTF_8)

private val refreshJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class TokenRefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

/**
 * Result of a successful token refresh.
 */
data class RefreshedTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Absolute epoch-ms timestamp when the new access token expires. */
    val expiresAt: Long
)

/**
 * Refreshes an Anthropic OAuth access token using a refresh token.
 *
 * Hits `POST https://console.anthropic.com/v1/oauth/token` with
 * `grant_type=refresh_token`.
 *
 * @param refreshToken The refresh token from a previous OAuth login or credential file
 * @return [RefreshedTokens] with the new access token, new refresh token, and expiry
 * @throws Exception if the refresh request fails
 */
suspend fun refreshAnthropicToken(refreshToken: String): RefreshedTokens {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(refreshJson) }
    }

    return try {
        logger.info { "Refreshing Anthropic OAuth token..." }

        // OAuth 2.0 token endpoint requires application/x-www-form-urlencoded (not JSON)
        val response: HttpResponse = client.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("client_id", CLIENT_ID)
                append("refresh_token", refreshToken)
            }
        )

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Token refresh failed (${response.status.value}): $errorBody")
        }

        val responseText = response.bodyAsText()
        val data = refreshJson.decodeFromString<TokenRefreshResponse>(responseText)

        // 5-minute buffer before actual expiry (mirrors OpenClaw TS implementation)
        val expiresAt = System.currentTimeMillis() + data.expiresIn * 1000 - 5 * 60 * 1000

        logger.info { "Anthropic OAuth token refreshed successfully (expires: ${java.util.Date(expiresAt)})" }

        RefreshedTokens(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            expiresAt = expiresAt
        )
    } finally {
        client.close()
    }
}
