package com.kclaw.memory.embedding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OllamaEmbeddingsTest {

    private val provider = OllamaEmbeddings(model = "nomic-embed-text")

    @AfterEach
    fun tearDown() {
        provider.httpClientOverride = null
    }

    @Nested
    inner class HappyPath {
        @Test
        fun `embeds single text`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"embeddings":[[0.1,0.2,0.3]]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            val result = provider.embed(listOf("hello"))
            result shouldHaveSize 1
            result[0].size shouldBe 3
            result[0][0] shouldBe 0.1f
        }

        @Test
        fun `embeds batch of texts`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"embeddings":[[0.1,0.2],[0.3,0.4]]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            val result = provider.embed(listOf("hello", "world"))
            result shouldHaveSize 2
        }

        @Test
        fun `empty input returns empty list`() = runTest {
            val result = provider.embed(emptyList())
            result shouldHaveSize 0
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `server not running throws connection error`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                throw java.net.ConnectException("Connection refused")
            })
            val ex = shouldThrow<IllegalStateException> { provider.embed(listOf("test")) }
            ex.message shouldContain "Failed to connect to Ollama"
        }

        @Test
        fun `HTTP error propagates`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"error":"model not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            val ex = shouldThrow<IllegalStateException> { provider.embed(listOf("test")) }
            ex.message shouldContain "Ollama API error"
        }
    }

    @Nested
    inner class Configuration {
        @Test
        fun `custom baseUrl is used`() = runTest {
            val custom = OllamaEmbeddings(baseUrl = "http://myserver:11434")
            custom.httpClientOverride = HttpClient(MockEngine { request ->
                request.url.host shouldBe "myserver"
                respond(
                    content = """{"embeddings":[[0.1]]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            custom.embed(listOf("test"))
        }

        @Test
        fun `dimensions correct for nomic-embed-text`() {
            provider.dimensions shouldBe 768
        }

        @Test
        fun `provider name is ollama`() {
            provider.providerName shouldBe "ollama"
        }
    }
}
