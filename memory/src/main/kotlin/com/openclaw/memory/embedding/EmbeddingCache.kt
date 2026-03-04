package com.openclaw.memory.embedding

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.*

private val logger = KotlinLogging.logger {}

/**
 * Disk-based cache for embedding vectors.
 * Stores each embedding as raw float bytes at `cacheDir/<sha256>.bin`.
 */
class EmbeddingCache(private val cacheDir: Path) {

    init {
        cacheDir.createDirectories()
    }

    /** Compute SHA-256 hash of the content for use as cache key. */
    fun contentHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Retrieve a cached embedding, or null if not present. */
    fun get(hash: String): FloatArray? {
        val file = cacheDir.resolve("$hash.bin")
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(bytes.size / 4) { buffer.getFloat() }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read cache entry: $hash" }
            null
        }
    }

    /** Store an embedding in the cache. */
    fun put(hash: String, embedding: FloatArray) {
        val file = cacheDir.resolve("$hash.bin")
        val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in embedding) {
            buffer.putFloat(f)
        }
        file.writeBytes(buffer.array())
    }

    /** Number of cached entries. */
    fun size(): Int {
        if (!cacheDir.exists()) return 0
        return cacheDir.listDirectoryEntries("*.bin").size
    }

    /** Clear all cached entries. */
    fun clear() {
        if (!cacheDir.exists()) return
        cacheDir.listDirectoryEntries("*.bin").forEach { it.deleteExisting() }
    }
}
