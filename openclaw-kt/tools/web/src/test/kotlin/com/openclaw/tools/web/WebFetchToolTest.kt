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

class WebFetchToolTest {

    @BeforeEach
    fun setUp() {
        WebFetchTool.httpClientOverride = null
    }

    @AfterEach
    fun tearDown() {
        WebFetchTool.httpClientOverride = null
    }

    @Nested
    inner class HtmlFetching {

        @Test
        fun `fetches and extracts HTML page`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """
                        <html>
                        <head><title>Test Page</title></head>
                        <body>
                            <article>
                                <h1>Hello World</h1>
                                <p>This is a test paragraph.</p>
                            </article>
                        </body>
                        </html>
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://example.com"))
            result shouldContain "Hello World"
            result shouldContain "test paragraph"
            result shouldContain "Title: Test Page"
            result shouldContain "URL: https://example.com"
        }

        @Test
        fun `uses text extract mode when specified`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """
                        <html><body>
                            <p>Plain <strong>text</strong> content</p>
                        </body></html>
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            })

            val result = WebFetchTool.execute(
                WebFetchTool.Args(url = "https://example.com", extractMode = "text")
            )
            result shouldContain "Plain text content"
            result shouldNotContain "**text**"
        }
    }

    @Nested
    inner class JsonFetching {

        @Test
        fun `handles JSON response`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = """{"name":"test","value":42}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://api.example.com/data"))
            result shouldContain "\"name\": \"test\""
            result shouldContain "\"value\": 42"
        }
    }

    @Nested
    inner class PlainTextFetching {

        @Test
        fun `handles plain text response`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "Just plain text content here",
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://example.com/robots.txt"))
            result shouldContain "Just plain text content here"
        }
    }

    @Nested
    inner class ContentTruncation {

        @Test
        fun `truncates long content`() = runTest {
            val longContent = "x".repeat(100_000)
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = longContent,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebFetchTool.execute(
                WebFetchTool.Args(url = "https://example.com/large", maxChars = 1000)
            )
            result shouldContain "truncated at 1000 characters"
            // Result is maxChars + truncation notice
            result.length shouldBe (1000 + "\n\n[... truncated at 1000 characters ...]".length)
        }

        @Test
        fun `truncate leaves short text unchanged`() {
            val text = "Short text"
            WebFetchTool.truncate(text, 50000) shouldBe text
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `handles HTTP error response`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "Not Found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://example.com/missing"))
            result shouldContain "Error: HTTP 404"
        }

        @Test
        fun `handles connection error`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                throw java.net.ConnectException("Connection refused")
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://nonexistent.invalid"))
            result shouldContain "Error fetching"
            result shouldContain "Connection refused"
        }

        @Test
        fun `rejects non-HTTP URLs`() = runTest {
            val result = WebFetchTool.execute(WebFetchTool.Args(url = "file:///etc/passwd"))
            result shouldContain "Error: Only HTTP and HTTPS URLs are supported"
        }

        @Test
        fun `rejects ftp URLs`() = runTest {
            val result = WebFetchTool.execute(WebFetchTool.Args(url = "ftp://files.example.com/data"))
            result shouldContain "Error: Only HTTP and HTTPS URLs are supported"
        }

        @Test
        fun `handles server error`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://example.com/broken"))
            result shouldContain "Error: HTTP 500"
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `handles HTML detected by content sniffing`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "<!DOCTYPE html><html><body><p>Sniffed HTML</p></body></html>",
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            val result = WebFetchTool.execute(WebFetchTool.Args(url = "https://example.com/page"))
            result shouldContain "Sniffed HTML"
        }

        @Test
        fun `maxChars is clamped to valid range`() = runTest {
            WebFetchTool.httpClientOverride = HttpClient(MockEngine { _ ->
                respond(
                    content = "short",
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            })

            // maxChars below minimum should still work (clamped to 100)
            val result = WebFetchTool.execute(
                WebFetchTool.Args(url = "https://example.com", maxChars = 1)
            )
            result shouldContain "Web Fetch Result"
        }
    }
}
