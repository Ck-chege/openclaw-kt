package com.openclaw.agent.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

/**
 * Manages Anthropic OAuth credentials with automatic token refresh.
 *
 * Supports two credential sources, tried in order:
 *
 * 1. **Claude CLI credentials** (`~/.claude/.credentials.json`)
 *    - Zero-config: populated by `claude` CLI login
 *    - Full OAuth: has both access + refresh token → auto-refreshes
 *    - Setup-token only: has access token only → uses until expiry, then errors
 *
 * 2. **Explicit setup-token** (from config `ANTHROPIC_API_KEY` / `CLAUDE_SETUP_TOKEN`)
 *    - Pasted manually from `claude setup-token`
 *    - No refresh possible — user must re-paste when expired
 *
 * Thread-safe: uses a [Mutex] to prevent concurrent refresh races.
 */
open class AnthropicCredentialManager(
    /** Explicit setup-token or API key from config/env (optional). */
    private val explicitKey: String? = null,
    /** Override for home directory (for testing). */
    private val homeDir: String? = null
) {
    private val mutex = Mutex()

    // In-memory cache of the current credential state
    private var cachedCredential: ClaudeCliCredential? = null

    /**
     * Returns a valid Anthropic access token, refreshing if needed.
     *
     * Priority:
     * 1. Claude CLI full OAuth (auto-refresh)
     * 2. Claude CLI setup-token (no refresh)
     * 3. Explicit key from config
     *
     * @throws IllegalStateException if no credential is available or all are expired
     */
    suspend fun getAccessToken(): String {
        // If explicit key is a regular API key (not OAuth), return it directly
        if (explicitKey != null && !isAnthropicOAuthToken(explicitKey)) {
            return explicitKey
        }

        return mutex.withLock {
            resolveToken()
        }
    }

    /**
     * Returns true if any credential source is available (valid or refreshable).
     */
    fun hasCredential(): Boolean {
        if (explicitKey != null) return true
        return readCliCredential() != null
    }

    private suspend fun resolveToken(): String {
        // 1. Try CLI credentials (best path — full OAuth with auto-refresh)
        val cliCred = cachedCredential ?: readCliCredential()

        if (cliCred != null) {
            cachedCredential = cliCred

            if (!cliCred.isExpired()) {
                logger.debug { "Using cached CLI access token (expires: ${java.util.Date(cliCred.expiresAt)})" }
                return cliCred.accessToken
            }

            // Token is expired — try to refresh if we have a refresh token
            if (cliCred is ClaudeCliCredential.FullOAuth) {
                logger.info { "CLI access token expired, refreshing..." }
                return try {
                    val refreshed = doRefresh(cliCred.refreshToken)
                    cachedCredential = ClaudeCliCredential.FullOAuth(
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        expiresAt = refreshed.expiresAt,
                        subscriptionType = cliCred.subscriptionType
                    )
                    logger.info { "Token refreshed successfully" }
                    refreshed.accessToken
                } catch (e: Exception) {
                    logger.error { "Token refresh failed: ${e.message}" }
                    throw IllegalStateException(
                        "Anthropic OAuth token expired and refresh failed: ${e.message}. " +
                        "Please re-authenticate via the Claude CLI (`claude`) or paste a new setup-token.",
                        e
                    )
                }
            } else {
                // SetupTokenOnly — expired, can't refresh
                throw IllegalStateException(
                    "Anthropic setup-token has expired. " +
                    "Run `claude setup-token` and update CLAUDE_SETUP_TOKEN / ANTHROPIC_API_KEY in your config."
                )
            }
        }

        // 2. Fall back to explicit OAuth token from config/env
        if (explicitKey != null) {
            logger.debug { "Using explicit setup-token from config" }
            return explicitKey
        }

        throw IllegalStateException(
            "No Anthropic credentials found. Options:\n" +
            "  1. Log in with the Claude CLI: `claude auth login`\n" +
            "  2. Set ANTHROPIC_API_KEY to a regular API key (sk-ant-api*)\n" +
            "  3. Set CLAUDE_SETUP_TOKEN to a setup-token from `claude setup-token`"
        )
    }

    /** Reads the CLI credential from disk. Overridable for testing. */
    protected open fun readCliCredential(): ClaudeCliCredential? =
        readClaudeCliCredentials(homeDir)

    /** Performs the actual token refresh HTTP call. Overridable for testing. */
    protected open suspend fun doRefresh(refreshToken: String): RefreshedTokens =
        refreshAnthropicToken(refreshToken)

    /** Seeds the internal cache directly — for use in subclasses/tests only. */
    protected fun seedCredential(cred: ClaudeCliCredential) {
        cachedCredential = cred
    }

    /**
     * Clears the cached credential, forcing a re-read on next access.
     * Useful after manual credential rotation.
     */
    fun invalidate() {
        cachedCredential = null
    }
}

/**
 * Detects the auth mode for a given key string:
 * - null → check Claude CLI credentials file
 * - starts with "sk-ant-oat" → OAuth/setup-token
 * - starts with "sk-ant-api" → regular API key
 * - anything else → treat as API key
 */
fun detectAnthropicAuthMode(key: String?): AnthropicAuthMode {
    if (key == null) return AnthropicAuthMode.CLAUDE_CLI
    if (isAnthropicOAuthToken(key)) return AnthropicAuthMode.OAUTH_TOKEN
    return AnthropicAuthMode.API_KEY
}

enum class AnthropicAuthMode {
    /** Read from `~/.claude/.credentials.json`, auto-refresh full OAuth */
    CLAUDE_CLI,
    /** OAuth/setup-token (`sk-ant-oat*`), no auto-refresh if setup-token only */
    OAUTH_TOKEN,
    /** Regular API key (`sk-ant-api*`), no expiry */
    API_KEY
}
