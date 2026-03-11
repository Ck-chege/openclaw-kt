package com.kclaw.gateway.auth

import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class AuthRateLimiterTest {

    @Test
    fun `allows requests under the threshold`() {
        val limiter = AuthRateLimiter(maxAttempts = 5, windowMs = 60_000L)
        repeat(5) {
            limiter.check("192.168.1.1").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Allowed>()
        }
    }

    @Test
    fun `blocks after exceeding maxAttempts`() {
        val limiter = AuthRateLimiter(maxAttempts = 3, windowMs = 60_000L)
        repeat(3) { limiter.check("10.0.0.1") }
        limiter.check("10.0.0.1").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Blocked>()
    }

    @Test
    fun `different IPs are tracked independently`() {
        val limiter = AuthRateLimiter(maxAttempts = 2, windowMs = 60_000L)
        repeat(2) { limiter.check("ip-a") }
        // ip-a is at limit, but ip-b should still be allowed
        limiter.check("ip-b").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Allowed>()
        limiter.check("ip-a").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Blocked>()
    }

    @Test
    fun `reset clears the record for an IP`() {
        val limiter = AuthRateLimiter(maxAttempts = 2, windowMs = 60_000L)
        repeat(3) { limiter.check("10.0.0.2") }
        limiter.check("10.0.0.2").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Blocked>()
        limiter.reset("10.0.0.2")
        limiter.check("10.0.0.2").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Allowed>()
    }

    @Test
    fun `evictExpired removes old entries`() {
        val limiter = AuthRateLimiter(maxAttempts = 2, windowMs = 1L) // 1ms window
        repeat(3) { limiter.check("10.0.0.3") }
        Thread.sleep(10) // wait for window to expire
        limiter.evictExpired()
        // After eviction, the IP should be allowed again
        limiter.check("10.0.0.3").shouldBeInstanceOf<AuthRateLimiter.RateLimitResult.Allowed>()
    }
}
