package com.openclaw.agent.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClaudeCliCredentialReaderTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeCredentials(json: String): String {
        val claudeDir = File(tempDir.toFile(), ".claude")
        claudeDir.mkdirs()
        File(claudeDir, ".credentials.json").writeText(json)
        return tempDir.toFile().absolutePath
    }

    @Test
    fun `returns null when credentials file does not exist`() {
        val result = readClaudeCliCredentials(homeDir = tempDir.toFile().absolutePath)
        result shouldBe null
    }

    @Test
    fun `returns FullOAuth when both access and refresh tokens present`() {
        val expiresAt = System.currentTimeMillis() + 3_600_000
        val homeDir = writeCredentials("""
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-access-token",
                "refreshToken": "refresh-token-xyz",
                "expiresAt": $expiresAt,
                "subscriptionType": "max"
              }
            }
        """.trimIndent())

        val result = readClaudeCliCredentials(homeDir)

        result.shouldBeInstanceOf<ClaudeCliCredential.FullOAuth>()
        result as ClaudeCliCredential.FullOAuth
        result.accessToken shouldBe "sk-ant-oat01-access-token"
        result.refreshToken shouldBe "refresh-token-xyz"
        result.expiresAt shouldBe expiresAt
        result.subscriptionType shouldBe "max"
    }

    @Test
    fun `returns SetupTokenOnly when refresh token is absent`() {
        val expiresAt = System.currentTimeMillis() + 3_600_000
        val homeDir = writeCredentials("""
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-setup-token",
                "expiresAt": $expiresAt
              }
            }
        """.trimIndent())

        val result = readClaudeCliCredentials(homeDir)

        result.shouldBeInstanceOf<ClaudeCliCredential.SetupTokenOnly>()
        result as ClaudeCliCredential.SetupTokenOnly
        result.accessToken shouldBe "sk-ant-oat01-setup-token"
        result.expiresAt shouldBe expiresAt
    }

    @Test
    fun `returns null when claudeAiOauth is missing`() {
        val homeDir = writeCredentials("""{ "somethingElse": {} }""")
        readClaudeCliCredentials(homeDir) shouldBe null
    }

    @Test
    fun `returns null when accessToken is blank`() {
        val homeDir = writeCredentials("""
            {
              "claudeAiOauth": {
                "accessToken": "",
                "refreshToken": "refresh-xyz",
                "expiresAt": ${System.currentTimeMillis() + 3_600_000}
              }
            }
        """.trimIndent())
        readClaudeCliCredentials(homeDir) shouldBe null
    }

    @Test
    fun `returns null when expiresAt is missing`() {
        val homeDir = writeCredentials("""
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-token"
              }
            }
        """.trimIndent())
        readClaudeCliCredentials(homeDir) shouldBe null
    }

    @Test
    fun `returns null when file contains invalid JSON`() {
        val homeDir = writeCredentials("not-valid-json{{{")
        readClaudeCliCredentials(homeDir) shouldBe null
    }

    @Test
    fun `ignores unknown fields in JSON`() {
        val expiresAt = System.currentTimeMillis() + 3_600_000
        val homeDir = writeCredentials("""
            {
              "claudeAiOauth": {
                "accessToken": "sk-ant-oat01-token",
                "refreshToken": "refresh-xyz",
                "expiresAt": $expiresAt,
                "unknownFutureField": "ignored"
              },
              "anotherUnknownTopLevel": true
            }
        """.trimIndent())

        val result = readClaudeCliCredentials(homeDir)
        result.shouldBeInstanceOf<ClaudeCliCredential.FullOAuth>()
    }

    @Test
    fun `isExpired returns true when token is past expiry`() {
        val expired = ClaudeCliCredential.SetupTokenOnly(
            accessToken = "sk-ant-oat01-expired",
            expiresAt = System.currentTimeMillis() - 1_000
        )
        expired.isExpired() shouldBe true
    }

    @Test
    fun `isExpired returns false when token is fresh`() {
        val fresh = ClaudeCliCredential.SetupTokenOnly(
            accessToken = "sk-ant-oat01-fresh",
            expiresAt = System.currentTimeMillis() + 3_600_000 // 1 hour — well beyond the 5-min buffer
        )
        fresh.isExpired() shouldBe false
    }

    @Test
    fun `canRefresh returns true for FullOAuth`() {
        val cred = ClaudeCliCredential.FullOAuth(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 3_600_000
        )
        cred.canRefresh() shouldBe true
    }

    @Test
    fun `canRefresh returns false for SetupTokenOnly`() {
        val cred = ClaudeCliCredential.SetupTokenOnly(
            accessToken = "access",
            expiresAt = System.currentTimeMillis() + 3_600_000
        )
        cred.canRefresh() shouldBe false
    }
}
