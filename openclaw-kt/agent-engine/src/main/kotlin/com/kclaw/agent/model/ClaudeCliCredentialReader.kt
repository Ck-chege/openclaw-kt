package com.kclaw.agent.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

// Path where the Claude CLI stores OAuth credentials on Linux/Windows
private const val CLAUDE_CREDENTIALS_PATH = ".claude/.credentials.json"

private val credentialJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * The raw JSON structure inside `~/.claude/.credentials.json`.
 */
@Serializable
private data class ClaudeCredentialsFile(
    @SerialName("claudeAiOauth")
    val claudeAiOauth: ClaudeAiOauth? = null
)

@Serializable
private data class ClaudeAiOauth(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val subscriptionType: String? = null
)

/**
 * Represents a credential read from the Claude CLI credential store.
 *
 * - [FullOAuth]: has both access + refresh token — can auto-refresh
 * - [SetupTokenOnly]: only an access token (from `claude setup-token`) — no refresh possible
 */
sealed class ClaudeCliCredential {
    abstract val accessToken: String
    abstract val expiresAt: Long

    data class FullOAuth(
        override val accessToken: String,
        val refreshToken: String,
        override val expiresAt: Long,
        val subscriptionType: String? = null
    ) : ClaudeCliCredential()

    data class SetupTokenOnly(
        override val accessToken: String,
        override val expiresAt: Long
    ) : ClaudeCliCredential()

    fun isExpired(bufferMs: Long = 5 * 60 * 1000): Boolean =
        System.currentTimeMillis() >= expiresAt - bufferMs

    fun canRefresh(): Boolean = this is FullOAuth
}

/**
 * Reads Claude CLI OAuth credentials from `~/.claude/.credentials.json`.
 *
 * This is the zero-config path — users who have logged in via the Claude CLI
 * (`claude`) automatically have their credentials available here. No setup-token
 * copy-paste required.
 *
 * Returns null if the file doesn't exist or contains no usable credentials.
 */
fun readClaudeCliCredentials(homeDir: String? = null): ClaudeCliCredential? {
    val base = homeDir ?: System.getProperty("user.home") ?: return null
    val credFile = java.io.File(base, CLAUDE_CREDENTIALS_PATH)

    if (!credFile.exists()) {
        logger.debug { "Claude CLI credentials file not found at: ${credFile.absolutePath}" }
        return null
    }

    return try {
        val raw = credFile.readText()
        val parsed = credentialJson.decodeFromString<ClaudeCredentialsFile>(raw)
        val oauth = parsed.claudeAiOauth ?: return null

        val accessToken = oauth.accessToken?.trim().takeIf { !it.isNullOrBlank() }
            ?: return null
        val expiresAt = oauth.expiresAt?.takeIf { it > 0 }
            ?: return null

        val refreshToken = oauth.refreshToken?.trim().takeIf { !it.isNullOrBlank() }

        if (refreshToken != null) {
            logger.debug { "Read full OAuth credentials from Claude CLI (expires: ${java.util.Date(expiresAt)})" }
            ClaudeCliCredential.FullOAuth(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                subscriptionType = oauth.subscriptionType
            )
        } else {
            logger.debug { "Read setup-token-only credentials from Claude CLI" }
            ClaudeCliCredential.SetupTokenOnly(
                accessToken = accessToken,
                expiresAt = expiresAt
            )
        }
    } catch (e: Exception) {
        logger.warn { "Failed to read Claude CLI credentials: ${e.message}" }
        null
    }
}
