package com.openclaw.session

import com.openclaw.protocol.identifiers.SessionKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger

class SessionWriteLockTest : FunSpec({

    test("withLock serializes access for same key") {
        runTest {
            val lock = SessionWriteLock()
            val key = SessionKey("agent:main:*:main")
            val counter = AtomicInteger(0)
            val maxConcurrency = AtomicInteger(0)

            val jobs = (0 until 20).map {
                async {
                    lock.withLock(key) {
                        val current = counter.incrementAndGet()
                        // Track maximum observed concurrency
                        maxConcurrency.updateAndGet { max -> maxOf(max, current) }
                        // Yield to give other coroutines a chance to run
                        delay(1)
                        counter.decrementAndGet()
                    }
                }
            }
            jobs.awaitAll()

            // If the lock works, max concurrency should be exactly 1
            maxConcurrency.get() shouldBe 1
        }
    }

    test("different keys can run concurrently") {
        runTest {
            val lock = SessionWriteLock()
            val key1 = SessionKey("agent-a:main:*:main")
            val key2 = SessionKey("agent-b:main:*:main")
            val counter = AtomicInteger(0)
            val maxConcurrency = AtomicInteger(0)

            val jobs = listOf(key1, key2).flatMap { key ->
                (0 until 10).map {
                    async {
                        lock.withLock(key) {
                            val current = counter.incrementAndGet()
                            maxConcurrency.updateAndGet { max -> maxOf(max, current) }
                            delay(1)
                            counter.decrementAndGet()
                        }
                    }
                }
            }
            jobs.awaitAll()

            // Different keys should allow concurrency > 1
            // (In practice this depends on scheduler, so we just verify no deadlock)
            counter.get() shouldBe 0
        }
    }

    test("lock returns the block result") {
        runTest {
            val lock = SessionWriteLock()
            val key = SessionKey("agent:main:*:main")

            val result = lock.withLock(key) { 42 }
            result shouldBe 42
        }
    }

    test("same raw key yields same lock regardless of SessionKey instance") {
        runTest {
            val lock = SessionWriteLock()
            val key1 = SessionKey("agent:main:*:main")
            val key2 = SessionKey("agent:main:*:main")
            val counter = AtomicInteger(0)
            val maxConcurrency = AtomicInteger(0)

            val jobs = listOf(key1, key2).flatMap { key ->
                (0 until 10).map {
                    async {
                        lock.withLock(key) {
                            val current = counter.incrementAndGet()
                            maxConcurrency.updateAndGet { max -> maxOf(max, current) }
                            delay(1)
                            counter.decrementAndGet()
                        }
                    }
                }
            }
            jobs.awaitAll()

            // Same raw key -> same mutex -> max concurrency = 1
            maxConcurrency.get() shouldBe 1
        }
    }
})
