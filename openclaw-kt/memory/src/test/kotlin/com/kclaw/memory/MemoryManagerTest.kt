package com.kclaw.memory

import com.kclaw.gateway.config.MemoryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MemoryManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class ProviderCreation {
        @Test
        fun `creates OpenAI provider with API key`() {
            val manager = MemoryManager(
                config = MemoryConfig(enabled = true, provider = "openai", model = "text-embedding-3-small"),
                workspacePath = tempDir,
                apiKeyResolver = { "test-api-key" }
            )
            manager.embeddingProvider.providerName shouldBe "openai"
        }

        @Test
        fun `creates Ollama provider without API key`() {
            val manager = MemoryManager(
                config = MemoryConfig(enabled = true, provider = "ollama", model = "nomic-embed-text"),
                workspacePath = tempDir,
                apiKeyResolver = { null }
            )
            manager.embeddingProvider.providerName shouldBe "ollama"
        }

        @Test
        fun `throws for missing OpenAI API key`() {
            val ex = shouldThrow<IllegalStateException> {
                MemoryManager(
                    config = MemoryConfig(enabled = true, provider = "openai"),
                    workspacePath = tempDir,
                    apiKeyResolver = { null }
                )
            }
            ex.message shouldContain "OPENAI_API_KEY"
        }

        @Test
        fun `throws for unknown provider`() {
            shouldThrow<IllegalArgumentException> {
                MemoryManager(
                    config = MemoryConfig(enabled = true, provider = "unknown"),
                    workspacePath = tempDir,
                    apiKeyResolver = { null }
                )
            }
        }
    }

    @Nested
    inner class Status {
        @Test
        fun `status returns correct info`() {
            val manager = MemoryManager(
                config = MemoryConfig(enabled = true, provider = "ollama", model = "nomic-embed-text"),
                workspacePath = tempDir,
                apiKeyResolver = { null }
            )
            val status = manager.status()
            status.enabled shouldBe true
            status.indexed shouldBe false
            status.totalChunks shouldBe 0
            status.embeddingProvider shouldBe "ollama"
        }
    }

    @Nested
    inner class EnabledFlag {
        @Test
        fun `isEnabled reflects config`() {
            val manager = MemoryManager(
                config = MemoryConfig(enabled = true, provider = "ollama"),
                workspacePath = tempDir,
                apiKeyResolver = { null }
            )
            manager.isEnabled shouldBe true
        }
    }
}
