package com.openclaw.agent.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RetryingExecutorTest {

    @Nested
    inner class IsRetryable {

        @Test
        fun `429 status code is retryable`() {
            val e = RuntimeException("Status code: 429 Error body: rate limit exceeded")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `rate_limit_exceeded is retryable`() {
            val e = RuntimeException("rate_limit_exceeded for gpt-4o")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `rate limit with spaces is retryable`() {
            val e = RuntimeException("Rate limit reached for organization")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `too many requests is retryable`() {
            val e = RuntimeException("Too Many Requests")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `500 server error is retryable`() {
            val e = RuntimeException("Status code: 500 Internal Server Error")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `502 bad gateway is retryable`() {
            val e = RuntimeException("Status code: 502 Bad Gateway")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `503 service unavailable is retryable`() {
            val e = RuntimeException("Status code: 503 Service Unavailable")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `529 overloaded is retryable`() {
            val e = RuntimeException("Status code: 529 Overloaded")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `overloaded message is retryable`() {
            val e = RuntimeException("Anthropic API is temporarily overloaded")
            RetryingExecutor.isRetryable(e) shouldBe true
        }

        @Test
        fun `401 unauthorized is NOT retryable`() {
            val e = RuntimeException("Status code: 401 Unauthorized")
            RetryingExecutor.isRetryable(e) shouldBe false
        }

        @Test
        fun `400 bad request is NOT retryable`() {
            val e = RuntimeException("Status code: 400 Bad Request")
            RetryingExecutor.isRetryable(e) shouldBe false
        }

        @Test
        fun `null message is NOT retryable`() {
            val e = RuntimeException()
            RetryingExecutor.isRetryable(e) shouldBe false
        }

        @Test
        fun `generic error is NOT retryable`() {
            val e = RuntimeException("Some random error")
            RetryingExecutor.isRetryable(e) shouldBe false
        }
    }

    @Nested
    inner class ParseRetryAfter {

        @Test
        fun `parses 'try again in Xs' format`() {
            val msg = "Please try again in 10.468s. Visit https://example.com"
            val ms = RetryingExecutor.parseRetryAfter(msg)
            ms shouldNotBe null
            ms shouldBe 10468L
        }

        @Test
        fun `parses integer seconds`() {
            val msg = "try again in 5s"
            val ms = RetryingExecutor.parseRetryAfter(msg)
            ms shouldBe 5000L
        }

        @Test
        fun `parses retry-after header format`() {
            val msg = "retry-after: 30"
            val ms = RetryingExecutor.parseRetryAfter(msg)
            ms shouldBe 30000L
        }

        @Test
        fun `returns null for no retry hint`() {
            val ms = RetryingExecutor.parseRetryAfter("Some generic error message")
            ms shouldBe null
        }

        @Test
        fun `returns null for null message`() {
            val ms = RetryingExecutor.parseRetryAfter(null)
            ms shouldBe null
        }

        @Test
        fun `parses real OpenAI error message`() {
            val msg = """Rate limit reached for gpt-4o in organization org-xyz on tokens per min (TPM): Limit 30000, Used 18441, Requested 16793. Please try again in 10.468s."""
            val ms = RetryingExecutor.parseRetryAfter(msg)
            ms shouldBe 10468L
        }
    }
}
