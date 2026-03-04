package com.openclaw.memory.embedding

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

class OpenAIEmbeddingsTest {

    private val provider = OpenAIEmbeddings(apiKey = "test-key", model = "text-embedding-3-small")

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
                    content = """{"data":[{"embedding":[0.1,0.2,0.3]}]}""",
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
                    content = """{"data":[{"embedding":[0.1,0.2]},{"embedding":[0.3,0.4]}]}""",
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
        fun `401 throws authentication error`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"error":{"message":"Invalid API key"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            val ex = shouldThrow<IllegalStateException> { provider.embed(listOf("test")) }
            ex.message shouldContain "authentication failed"
        }

        @Test
        fun `429 throws rate limit error`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"error":{"message":"Rate limit exceeded"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
            val ex = shouldThrow<IllegalStateException> { provider.embed(listOf("test")) }
            ex.message shouldContain "rate limit"
        }

        @Test
        fun `connection failure propagates`() = runTest {
            provider.httpClientOverride = HttpClient(MockEngine { _ ->
                throw java.net.ConnectException("Connection refused")
            })
            shouldThrow<java.net.ConnectException> { provider.embed(listOf("test")) }
        }
    }

    @Nested
    inner class Configuration {
        @Test
        fun `dimensions correct for text-embedding-3-small`() {
            provider.dimensions shouldBe 1536
        }

        @Test
        fun `provider name is openai`() {
            provider.providerName shouldBe "openai"
        }
    }
}
