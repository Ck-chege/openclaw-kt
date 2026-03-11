package com.kclaw.gateway.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-IP rate limiter for auth failures.
 * Uses ConcurrentHashMap.compute for atomic updates without explicit locking.
 *
 * [maxAttempts] failures within [windowMs] milliseconds → [RateLimitResult.Blocked].
 * On success, call [reset] to clear the record for that IP.
 */
class AuthRateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMs: Long = 60_000L
) {
    private data class Record(val count: AtomicInteger, val windowStart: Long)
    private val records = ConcurrentHashMap<String, Record>()

    sealed class RateLimitResult {
        data object Allowed : RateLimitResult()
        data class Blocked(val retryAfterMs: Long) : RateLimitResult()
    }

    fun check(ip: String): RateLimitResult {
        val now = System.currentTimeMillis()
        val record = records.compute(ip) { _, existing ->
            when {
                existing == null || (now - existing.windowStart) > windowMs ->
                    Record(AtomicInteger(1), now)
                else -> existing.also { it.count.incrementAndGet() }
            }
        }!!
        return if (record.count.get() > maxAttempts) {
            val retryAfterMs = windowMs - (now - record.windowStart)
            RateLimitResult.Blocked(retryAfterMs.coerceAtLeast(0L))
        } else {
            RateLimitResult.Allowed
        }
    }

    fun reset(ip: String) {
        records.remove(ip)
    }

    /** Clean up expired windows to prevent unbounded memory growth. */
    fun evictExpired() {
        val now = System.currentTimeMillis()
        records.entries.removeIf { (_, record) -> (now - record.windowStart) > windowMs }
    }
}
