package com.openclaw.tools.web

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WebSearchToolTest {

    @BeforeEach
    fun setUp() {
        WebSearchTool.httpClientOverride = null
        WebSearchTool.apiKeyOverride = null
        WebSearchTool.clearCache()
    }

    @AfterEach
    fun tearDown() {
        WebSearchTool.httpClientOverride = null
        WebSearchTool.apiKeyOverride = null
        WebSearchTool.clearCache()
    }

    private val sampleSearchResponse = """
        {
            "web": {
                "results": [
                    {
                        "title": "Kotlin Coroutines Guide",
                        "url": "https://kotlinlang.org/docs/coroutines-guide.html",
                        "description": "A guide to Kotlin coroutines with examples.",
                        "age": "2 days ago"
                    },
                    {
                        "title": "Kotlin Official Site",
                        "url": "https://kotlinlang.org",
                        "description": "The official Kotlin programming language website.",
                        "age": "1 week ago"
                    }
                ]
            }
        }
    """.trimIndent()

    @Nested
    inner class Availability {

        @Test
        fun `isAvailable returns false when no API key`() {
            WebSearchTool.apiKeyOverride = ""
            WebSearchTool.isAvailable() shouldBe false
        }

        @Test
        fun `isAvailable returns true when API key is set`() {
            WebSearchTool.apiKeyOverride = "test-key-123"
            WebSearchTool.isAvailable() shouldBe true
        }
    }

    @Nested
    inner class SearchExecution {

        @Test
        fun `execute returns results for valid query`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "kotlin coroutines"))
            result shouldContain "Kotlin Coroutines Guide"
            result shouldContain "https://kotlinlang.org/docs/coroutines-guide.html"
            result shouldContain "A guide to Kotlin coroutines"
            result shouldContain "Kotlin Official Site"
        }

        @Test
        fun `execute returns error when no API key`() = runTest {
            WebSearchTool.apiKeyOverride = ""

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "test"))
            result shouldContain "Error: BRAVE_API_KEY"
            result shouldContain "not set"
        }

        @Test
        fun `execute returns error for blank query`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "   "))
            result shouldContain "Error"
            result shouldContain "empty"
        }
    }

    @Nested
    inner class ApiErrors {

        @Test
        fun `handles 429 rate limit error`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"error":"rate limit exceeded"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "test"))
            result shouldContain "Error"
            result shouldContain "429"
        }

        @Test
        fun `handles 500 server error`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "test"))
            result shouldContain "Error"
            result shouldContain "500"
        }

        @Test
        fun `handles connection failure`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                throw java.net.ConnectException("Connection refused")
            })

            val result = WebSearchTool.execute(WebSearchTool.Args(query = "test"))
            result shouldContain "Error searching web"
            result shouldContain "Connection refused"
        }
    }

    @Nested
    inner class QueryParameters {

        @Test
        fun `respects count parameter`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var capturedUrl = ""

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { request ->
                capturedUrl = request.url.toString()
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "test", count = 3))
            capturedUrl shouldContain "count=3"
        }

        @Test
        fun `sends country parameter when provided`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var capturedUrl = ""

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { request ->
                capturedUrl = request.url.toString()
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "test", country = "CA"))
            capturedUrl shouldContain "country=CA"
        }

        @Test
        fun `sends freshness parameter when provided`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var capturedUrl = ""

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { request ->
                capturedUrl = request.url.toString()
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "test", freshness = "week"))
            capturedUrl shouldContain "freshness=week"
        }

        @Test
        fun `clamps count to valid range`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var capturedUrl = ""

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { request ->
                capturedUrl = request.url.toString()
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "test", count = 50))
            capturedUrl shouldContain "count=10" // clamped to max
        }

        @Test
        fun `sends auth header`() = runTest {
            WebSearchTool.apiKeyOverride = "my-secret-key"
            var capturedAuthHeader = ""

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { request ->
                capturedAuthHeader = request.headers["X-Subscription-Token"] ?: ""
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "test"))
            capturedAuthHeader shouldBe "my-secret-key"
        }
    }

    @Nested
    inner class Caching {

        @Test
        fun `caching returns same result within TTL`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var callCount = 0

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                callCount++
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val result1 = WebSearchTool.execute(WebSearchTool.Args(query = "kotlin coroutines"))
            val result2 = WebSearchTool.execute(WebSearchTool.Args(query = "kotlin coroutines"))

            result1 shouldBe result2
            callCount shouldBe 1 // Only one HTTP call made
        }

        @Test
        fun `different queries are not cached together`() = runTest {
            WebSearchTool.apiKeyOverride = "test-key"
            var callCount = 0

            WebSearchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                callCount++
                respond(
                    content = sampleSearchResponse,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            WebSearchTool.execute(WebSearchTool.Args(query = "query1"))
            WebSearchTool.execute(WebSearchTool.Args(query = "query2"))

            callCount shouldBe 2
        }
    }

    @Nested
    inner class ResultParsing {

        @Test
        fun `parses results with all fields`() {
            val result = WebSearchTool.parseResults(sampleSearchResponse, "kotlin")
            result shouldContain "1. Kotlin Coroutines Guide"
            result shouldContain "URL: https://kotlinlang.org/docs/coroutines-guide.html"
            result shouldContain "A guide to Kotlin coroutines"
            result shouldContain "Age: 2 days ago"
            result shouldContain "2. Kotlin Official Site"
        }

        @Test
        fun `handles empty results`() {
            val emptyResponse = """{"web": {"results": []}}"""
            val result = WebSearchTool.parseResults(emptyResponse, "nothing")
            result shouldContain "No results found"
        }

        @Test
        fun `handles missing web results key`() {
            val noWebResponse = """{"query": {"original": "test"}}"""
            val result = WebSearchTool.parseResults(noWebResponse, "test")
            result shouldContain "No results found"
        }

        @Test
        fun `handles results with missing optional fields`() {
            val partialResponse = """
                {
                    "web": {
                        "results": [
                            {
                                "title": "Minimal Result",
                                "url": "https://example.com"
                            }
                        ]
                    }
                }
            """.trimIndent()

            val result = WebSearchTool.parseResults(partialResponse, "test")
            result shouldContain "Minimal Result"
            result shouldContain "https://example.com"
            result shouldNotContain "Age:"
        }
    }
}
