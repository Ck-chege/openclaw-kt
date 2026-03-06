package com.openclaw.agent.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AnthropicCredentialManagerTest {

    private val futureExpiry = System.currentTimeMillis() + 3_600_000L
    private val pastExpiry = System.currentTimeMillis() - 1_000L

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun managerWithCli(cred: ClaudeCliCredential?, explicitKey: String? = null) =
        AnthropicCredentialManager(
            explicitKey = explicitKey,
            homeDir = null,
            // Inject credential directly — no real file I/O in tests
        ).also {
            // Seed via the credential reader override path by using the internal helper
        }

    /** Manager that reads the given CLI credential (bypasses file system). */
    private fun managerWith(
        cliCred: ClaudeCliCredential?,
        explicitKey: String? = null,
        refresher: (suspend (String) -> RefreshedTokens)? = null
    ): AnthropicCredentialManager =
        TestableCredentialManager(cliCred, explicitKey, refresher)

    // ── Tests: explicit API key ───────────────────────────────────────────────

    @Test
    fun `returns explicit API key directly without touching CLI`() = runTest {
        val manager = managerWith(cliCred = null, explicitKey = "sk-ant-api03-real-key")
        manager.getAccessToken() shouldBe "sk-ant-api03-real-key"
    }

    // ── Tests: fresh CLI OAuth token ──────────────────────────────────────────

    @Test
    fun `returns fresh CLI OAuth access token`() = runTest {
        val cred = ClaudeCliCredential.FullOAuth(
            accessToken = "sk-ant-oat01-fresh",
            refreshToken = "refresh-xyz",
            expiresAt = futureExpiry
        )
        val manager = managerWith(cliCred = cred)
        manager.getAccessToken() shouldBe "sk-ant-oat01-fresh"
    }

    @Test
    fun `returns fresh SetupTokenOnly access token`() = runTest {
        val cred = ClaudeCliCredential.SetupTokenOnly(
            accessToken = "sk-ant-oat01-setup",
            expiresAt = futureExpiry
        )
        val manager = managerWith(cliCred = cred)
        manager.getAccessToken() shouldBe "sk-ant-oat01-setup"
    }

    // ── Tests: expired token with refresh ────────────────────────────────────

    @Test
    fun `auto-refreshes expired FullOAuth token`() = runTest {
        val expired = ClaudeCliCredential.FullOAuth(
            accessToken = "sk-ant-oat01-old",
            refreshToken = "refresh-token",
            expiresAt = pastExpiry
        )
        val newTokens = RefreshedTokens(
            accessToken = "sk-ant-oat01-new",
            refreshToken = "new-refresh-token",
            expiresAt = futureExpiry
        )
        val manager = managerWith(cliCred = expired, refresher = { _ -> newTokens })
        manager.getAccessToken() shouldBe "sk-ant-oat01-new"
    }

    @Test
    fun `returns new token on subsequent call after refresh`() = runTest {
        val expired = ClaudeCliCredential.FullOAuth(
            accessToken = "sk-ant-oat01-old",
            refreshToken = "refresh-token",
            expiresAt = pastExpiry
        )
        var refreshCallCount = 0
        val manager = managerWith(cliCred = expired, refresher = { _ ->
            refreshCallCount++
            RefreshedTokens("sk-ant-oat01-new", "new-refresh", futureExpiry)
        })

        manager.getAccessToken() shouldBe "sk-ant-oat01-new"
        manager.getAccessToken() shouldBe "sk-ant-oat01-new" // cached — no second refresh
        refreshCallCount shouldBe 1
    }

    // ── Tests: expired token without refresh ─────────────────────────────────

    @Test
    fun `throws when SetupTokenOnly is expired`() = runTest {
        val expired = ClaudeCliCredential.SetupTokenOnly(
            accessToken = "sk-ant-oat01-expired",
            expiresAt = pastExpiry
        )
        val manager = managerWith(cliCred = expired)
        val ex = shouldThrow<IllegalStateException> { manager.getAccessToken() }
        ex.message shouldContain "expired"
    }

    @Test
    fun `throws with helpful message when refresh fails`() = runTest {
        val expired = ClaudeCliCredential.FullOAuth(
            accessToken = "sk-ant-oat01-old",
            refreshToken = "bad-refresh",
            expiresAt = pastExpiry
        )
        val manager = managerWith(cliCred = expired, refresher = { _ ->
            throw Exception("401 Unauthorized")
        })
        val ex = shouldThrow<IllegalStateException> { manager.getAccessToken() }
        ex.message shouldContain "refresh failed"
    }

    // ── Tests: no credentials ─────────────────────────────────────────────────

    @Test
    fun `throws with setup instructions when no credentials at all`() = runTest {
        val manager = managerWith(cliCred = null, explicitKey = null)
        val ex = shouldThrow<IllegalStateException> { manager.getAccessToken() }
        ex.message shouldContain "No Anthropic credentials found"
        ex.message shouldContain "ANTHROPIC_API_KEY"
    }

    // ── Tests: detectAnthropicAuthMode ────────────────────────────────────────

    @Test
    fun `detectAnthropicAuthMode returns CLAUDE_CLI for null key`() {
        detectAnthropicAuthMode(null) shouldBe AnthropicAuthMode.CLAUDE_CLI
    }

    @Test
    fun `detectAnthropicAuthMode returns OAUTH_TOKEN for setup-token`() {
        detectAnthropicAuthMode("sk-ant-oat01-abc") shouldBe AnthropicAuthMode.OAUTH_TOKEN
    }

    @Test
    fun `detectAnthropicAuthMode returns API_KEY for regular key`() {
        detectAnthropicAuthMode("sk-ant-api03-abc") shouldBe AnthropicAuthMode.API_KEY
    }

    @Test
    fun `isAnthropicOAuthToken detects oat prefix`() {
        isAnthropicOAuthToken("sk-ant-oat01-sometoken") shouldBe true
        isAnthropicOAuthToken("sk-ant-api03-somekey") shouldBe false
        isAnthropicOAuthToken("") shouldBe false
    }
}

/**
 * Test-only subclass of [AnthropicCredentialManager] that injects a credential
 * directly and allows overriding the token refresher.
 */
private class TestableCredentialManager(
    private val cliCred: ClaudeCliCredential?,
    explicitKey: String?,
    private val refresher: (suspend (String) -> RefreshedTokens)?
) : AnthropicCredentialManager(explicitKey = explicitKey, homeDir = null) {

    // Seed the internal cache with our test credential
    init {
        if (cliCred != null) seedCredential(cliCred)
    }

    override suspend fun doRefresh(refreshToken: String): RefreshedTokens {
        return refresher?.invoke(refreshToken)
            ?: throw IllegalStateException("No refresher configured in test")
    }

    override fun readCliCredential(): ClaudeCliCredential? = cliCred
}
