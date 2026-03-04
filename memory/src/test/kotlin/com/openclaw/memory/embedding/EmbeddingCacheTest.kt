package com.openclaw.memory.embedding

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EmbeddingCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class PutAndGet {
        @Test
        fun `round-trip stores and retrieves embedding`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
            val hash = cache.contentHash("hello")
            cache.put(hash, embedding)
            val retrieved = cache.get(hash)
            retrieved.shouldNotBeNull()
            retrieved.toList() shouldBe embedding.toList()
        }

        @Test
        fun `miss returns null`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            cache.get("nonexistent").shouldBeNull()
        }

        @Test
        fun `overwrite replaces existing`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            val hash = cache.contentHash("test")
            cache.put(hash, floatArrayOf(1f, 2f))
            cache.put(hash, floatArrayOf(3f, 4f, 5f))
            val retrieved = cache.get(hash)
            retrieved.shouldNotBeNull()
            retrieved.toList() shouldBe listOf(3f, 4f, 5f)
        }
    }

    @Nested
    inner class Persistence {
        @Test
        fun `persists across instances`() {
            val cacheDir = tempDir.resolve("cache")
            val cache1 = EmbeddingCache(cacheDir)
            val hash = cache1.contentHash("hello")
            cache1.put(hash, floatArrayOf(1f, 2f, 3f))

            val cache2 = EmbeddingCache(cacheDir)
            val retrieved = cache2.get(hash)
            retrieved.shouldNotBeNull()
            retrieved.toList() shouldBe listOf(1f, 2f, 3f)
        }
    }

    @Nested
    inner class SizeAndClear {
        @Test
        fun `size reports correct count`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            cache.size() shouldBe 0
            cache.put(cache.contentHash("a"), floatArrayOf(1f))
            cache.put(cache.contentHash("b"), floatArrayOf(2f))
            cache.size() shouldBe 2
        }

        @Test
        fun `clear removes all entries`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            cache.put(cache.contentHash("a"), floatArrayOf(1f))
            cache.put(cache.contentHash("b"), floatArrayOf(2f))
            cache.clear()
            cache.size() shouldBe 0
            cache.get(cache.contentHash("a")).shouldBeNull()
        }
    }

    @Nested
    inner class Hashing {
        @Test
        fun `consistent hashing for same content`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            cache.contentHash("hello") shouldBe cache.contentHash("hello")
        }

        @Test
        fun `different content produces different hash`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            val h1 = cache.contentHash("hello")
            val h2 = cache.contentHash("world")
            (h1 != h2) shouldBe true
        }

        @Test
        fun `unicode content hashes correctly`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            val hash = cache.contentHash("こんにちは世界 🌍")
            cache.put(hash, floatArrayOf(1f, 2f))
            cache.get(hash).shouldNotBeNull()
        }

        @Test
        fun `empty string hashes correctly`() {
            val cache = EmbeddingCache(tempDir.resolve("cache"))
            val hash = cache.contentHash("")
            cache.put(hash, floatArrayOf(1f))
            cache.get(hash).shouldNotBeNull()
        }
    }
}
